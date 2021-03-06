(ns pathom.sql
  (:refer-clojure :exclude [count])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [common.async :refer-macros [go-catch <?]]
            [clojure.string :as str]
            [clojure.set :as set]
            [clojure.walk :as walk]
            [cljs.core.async :refer [<! >! put! close!]]
            [cljs.core.async.impl.protocols :refer [Channel]]
            [cljs.spec.alpha :as s]
            [pathom.core :as p]
            [nodejs.knex :as knex]))

(s/def ::db any?)

(s/def ::table keyword?)
(s/def ::table-name string?)

(s/def ::field qualified-keyword?)
(s/def ::field-value (s/or :string string?
                           :fn fn?))
(s/def ::fields (s/map-of ::field ::field-value))
(s/def ::fields' (s/map-of keyword? ::field))

(s/def ::reader-map ::p/reader-map)

(s/def ::table-spec' (s/keys :req [::table ::table-name ::fields]))
(s/def ::table-spec (s/merge ::table-spec' (s/keys :req [::fields' ::reader-map])))

(s/def ::translate-value (s/or :string string?
                               :multiple #{::translate-multiple}))

(s/def ::translate-index (s/map-of keyword? ::translate-value))

(s/def ::schema' (s/coll-of ::table-spec'))
(s/def ::schema (and (s/keys :req [::translate-index])
                     (s/map-of ::table ::table-spec)))

(s/def ::row (s/map-of string? (s/or :string string?
                                     :number number?)))

(s/def ::union-selector keyword?)
(s/def ::query-cache (partial instance? IAtom))

(defn local-fields [fields]
  (into {} (filter (fn [[_ v]] (string? v))) fields))

(defn prepare-schema [schema]
  (assoc (zipmap (map ::table schema)
                 (map #(let [m (::fields %)
                             simple-fields (local-fields m)]
                        (assoc %
                          ::reader-map
                          (-> (zipmap (keys m)
                                      (map
                                        (fn [field]
                                          (if (fn? field)
                                            field
                                            (fn [{:keys [::row]}]
                                              (get row field))))
                                        (vals m)))
                              (assoc :db/table (constantly (::table %))))
                          ::fields simple-fields
                          ::fields' (zipmap (vals simple-fields) (keys simple-fields))))
                      schema))
    ::translate-index
    (apply merge-with (fn [a b]
                        (if (= a b) a ::translate-multiple))
      (zipmap (map ::table schema)
              (map ::table-name schema))
      (->> (map (comp local-fields ::fields) schema)))))

(s/fdef prepare-schema
  :args (s/cat :schema ::schema')
  :ret ::schema)

(defn variant? [value name]
  (and (vector? value)
       (= (first value) name)))

(s/def ::field-variant
  (s/cat :variant #{::f} :table (s/? keyword?) :field qualified-keyword?))

(defn translate-args [{:keys [::schema]} cmds]
  (let [{:keys [::translate-index]} schema]
    (walk/prewalk
      (fn [x]
        (cond
          (keyword? x)
          (if-let [v (get translate-index x)]
            (if (= v ::translate-multiple)
              (throw (ex-info (str "Multiple possibilities for key " x) {}))
              v)
            x)

          (s/valid? ::field-variant x)
          (let [{:keys [table field]} (s/conform ::field-variant x)
                table (or table (keyword (namespace field)))
                {::keys [table-name fields]} (get schema table)]
            (str table-name "." (get fields field)))

          :else
          x))
      cmds)))

(defn row-get
  ([{:keys [::table-spec] :as env} attr]
   (p/read-from (update env :ast merge {:key attr :dispatch-key attr})
                (::reader-map table-spec)))
  ([{:keys [::table-spec] :as env} row attr]
   (p/read-from (-> (assoc env ::row row)
                    (update :ast merge {:key attr :dispatch-key attr}))
                (::reader-map table-spec))))

(defn ensure-chan [x]
  (if (p/chan? x)
    x
    (go x)))

(defn parse-row [{:keys [::table ast ::union-selector parser] :as env} row]
  (go-catch
    (let [row' {:db/table table :db/id (row-get env row :db/id)}
          query (if (p/union-children? ast)
                  (let [union-type (-> (row-get env row union-selector)
                                       ensure-chan <?)]
                    (some-> ast :query (get union-type)))
                  (:query ast))]
      (if query
        (-> (merge
              row'
              (parser (assoc env ::row row) query))
            (p/read-chan-values) <?)
        row'))))

(defn query [{:keys [::db] :as env} cmds]
  (knex/query db (translate-args env cmds)))

(defn cached-query [{:keys [::query-cache] :as env} cmds]
  (if query-cache
    (go-catch
      (let [cache-key cmds]
        (if (contains? @query-cache cache-key)
          (get @query-cache cache-key)
          (let [res (<? (query env cmds))]
            (swap! query-cache assoc cache-key res)
            res))))
    (query env cmds)))

(defn sql-node [{:keys [::table ::schema] :as env} cmds]
  (assert (get schema table) (str "[Query SQL] No specs for table " table))
  (let [{:keys [::table-name ::reader-map] :as table-spec} (get schema table)]
    (go-catch
      (let [rows (<? (cached-query env (cons [:from table-name] cmds)))
            env (assoc env ::table-spec table-spec
                           ::p/reader [reader-map p/placeholder-node])]
        (<? (p/read-chan-seq #(parse-row env %) rows))))))

(defn sql-first-node [env cmds]
  (go-catch
    (-> (sql-node env cmds) <?
        (first)
        (or [:error :row-not-found]))))

(defn- ensure-list [x]
  (if (sequential? x) x [x]))

(defn sql-table-node
  [{:keys [ast ::schema] :as env} table]
  (assert (get schema table) (str "[Query Table] No specs for table " table))
  (let [{:keys [limit where sort]} (:params ast)
        limit (or limit 50)]
    (sql-node (assoc env ::table table)
              (cond-> [[:limit limit]]
                where (conj (if (vector? where)
                              (vec (concat [:where] where))
                              [:where where]) )
                sort (conj (concat [:orderBy] (ensure-list sort)))))))

(defn record->map [record fields]
  (reduce (fn [m [k v]]
            (assoc m k (get record v)))
          {}
          fields))

(defn find-by [{:keys [::schema] :as env} {:keys [db/table ::query] :as search}]
  (assert table "Table is required")
  (go-catch
    (let [{:keys [::table-name ::fields]} (get schema table)
          search (-> (dissoc search :db/table ::query)
                     (set/rename-keys fields))]
      (some-> (cached-query env
                            (cond-> [[:from table-name]
                                     [:where search]
                                     [:limit 1]]
                              query (concat query)))
              <? first (record->map fields) (assoc :db/table table)))))

(defn count
  ([env table] (count env table []))
  ([{:keys [::db ::schema] :as env} table cmds]
   (assert (get schema table) (str "No specs for table " table))
   (knex/query-count db (->> (cons [:from table] cmds)
                             (translate-args env)))))

(defn save [{:keys [::schema ::db]} {:keys [db/table db/id] :as record}]
  (assert table "Table is required")
  (go-catch
    (let [{:keys [::table-name ::fields]} (get schema table)
          js-record (-> record
                        (select-keys (keys fields))
                        (dissoc :db/id)
                        (set/rename-keys fields))]
      (if id
        (do
          (<? (knex/run db [[:from table-name]
                            [:where {(get fields :db/id) id}]
                            [:update js-record]]))
          record)
        (let [id (<? (knex/insert db table-name js-record (:db/id fields)))]
          (assoc record :db/id id))))))

;; RELATIONAL MAPPING

(defn has-one [foreign-table local-field]
  (with-meta
    (fn [env]
      (let [foreign-id (row-get env local-field)]
        (sql-first-node (assoc env ::table foreign-table) [[:where {:db/id foreign-id}]])))
    {::join-one true}))

(defn has-many [foreign-table foreign-field & [params]]
  (with-meta
    (fn [env]
      (sql-table-node
        (cond-> (update-in env [:ast :params :where]
                           #(assoc (or % {}) foreign-field (row-get env :db/id)))
          (:sort params) (update-in [:ast :params :sort] #(or % (:sort params))))
        foreign-table))
    {::join-many true}))
