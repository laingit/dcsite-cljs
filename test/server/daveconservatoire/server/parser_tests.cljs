(ns daveconservatoire.server.parser-tests
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.test :refer [is are run-tests async testing deftest do-report]]
            [cljs.core.async :refer [<!]]
            [common.async :refer [<?]]
            [pathom.sql :as ps]
            [daveconservatoire.server.parser :as p]
            [daveconservatoire.server.test-shared :as ts :refer [env]]
            [nodejs.knex :as knex]))

(deftest test-create-user
  (async done
    (go
      (try
        (<? (knex/raw (::ps/db env) "delete from User where email = ?" ["mary@email.com"]))
        (let [id (<? (p/create-user env {:user/name  "Mary"
                                         :user/email "mary@email.com"}))]
          (is (not (nil? (<? (ps/find-by env {:db/id    id
                                              :db/table :user}))))))
        (catch :default e
          (do-report
            {:type :error, :message (.-message e) :actual e})))
      (done))))

(deftest test-hit-video-view
  (async done
    (go
      (try
        (<? (knex/truncate (::ps/db env) "UserVideoView"))
        (testing "creates hit for empty record"
          (<? (p/hit-video-view env {:user-view/user-id   720
                                     :user-view/lesson-id 5}))
          (is (= (<? (knex/query-count ts/connection "UserVideoView" []))
                 1)))
        (testing "don't create new entry when last lesson is the same"
          (<? (p/hit-video-view env {:user-view/user-id   720
                                     :user-view/lesson-id 5}))
          (is (= (<? (knex/query-count ts/connection "UserVideoView" []))
                 1)))
        (testing "create new entry when lesson is different"
          (<? (p/hit-video-view env {:user-view/user-id   720
                                     :user-view/lesson-id 6}))
          (is (= (<? (knex/query-count ts/connection "UserVideoView" []))
                 2)))
        (catch :default e
          (js/console.log (.-stack e))
          (do-report
            {:type :error, :message (.-message e) :actual e})))
      (done))))

(deftest test-update-current-user
  (async done
    (go
      (try
        (<? (knex/raw ts/connection "update User set biog='' where id = 720" []))
        (testing "creates hit for empty record"
          (<? (p/update-current-user (assoc env
                                       :current-user-id 720)
                                     {:user/about "New Description"}))
          (is (= (-> (knex/query-first ts/connection "User" [[:where {"id" 720}]])
                     <? (get "biog"))
                 "New Description")))
        (catch :default e
          (do-report
            {:type :error, :message (.-message e) :actual e})))
      (done))))

(deftest test-compute-ex-answer
  (async done
    (go
      (try
        (<? (knex/truncate (::ps/db env) "UserExerciseAnswer"))
        (<? (ps/save env {:db/table   :user
                          :db/id      720
                          :user/score 1}))
        (testing "does nothing for unlogged users"
          (<? (p/compute-ex-answer env {:url/slug "bass-clef-reading"}))
          (is (zero? (<? (ps/count env :ex-answer)))))

        (testing "compute score for logged user"
          (<? (p/compute-ex-answer (assoc env :current-user-id 720)
                                   {:url/slug "bass-clef-reading"}))
          (is (= (<? (ps/count env :ex-answer))
                 1))
          (is (= (-> (ps/find-by env {:db/table :user :db/id 720})
                     <? :user/score)
                 2)))
        (catch :default e
          (do-report
            {:type :error, :message (.-message e) :actual e})))
      (done))))

(deftest parse-read-not-found
  (async done
    (go
      (is (= (<! (p/parse {} [:invalid]))
             {:invalid [:error :not-found]}))
      (done))))

(deftest parser-read-courses
  (async done
    (go
      (try
        (is (= (<? (p/parse env [:app/courses]))
               {:app/courses [{:db/id 4 :db/table :course} {:db/id 7 :db/table :course}]}))

        (is (= (<! (p/parse env [{:app/courses [:db/id :course/title]}]))
               {:app/courses [{:db/id 4 :course/title "Reading Music" :db/table :course}
                              {:db/id 7 :course/title "Music:  A Beginner's Guide" :db/table :course}]}))

        (is (= (<! (p/parse env [{:app/courses [:db/id :course/title]}]))
               {:app/courses [{:db/id 4 :course/title "Reading Music" :db/table :course}
                              {:db/id 7 :course/title "Music:  A Beginner's Guide" :db/table :course}]}))

        (is (= (<! (p/parse env [{:app/courses [:db/id :course/home-type]}]))
               {:app/courses [{:db/id 4 :course/home-type :course.type/multi-topic :db/table :course}
                              {:db/id 7 :course/home-type :course.type/multi-topic :db/table :course}]}))

        (is (= (<! (p/parse env [{:app/courses {:course.type/multi-topic [:db/id :course/home-type]
                                                                :course.type/single-topic [:db/id :course/title]}}]))
               {:app/courses [{:db/id 4 :course/home-type :course.type/multi-topic :db/table :course}
                              {:db/id 7 :course/home-type :course.type/multi-topic :db/table :course}]}))
        (catch :default e
          (do-report
            {:type :error, :message (.-message e) :actual e})))
      (done))))

(deftest test-parse-read-lesson-by-slug
  (async done
    (go
      (try
        (is (= (->> (p/parse env [{[:lesson/by-slug "percussion"] [:db/id :lesson/title]}]) <!)
               {[:lesson/by-slug "percussion"] {:db/id 9 :db/table :lesson :lesson/title "Percussion"}}))

        (is (= (->> (p/parse env [{[:lesson/by-slug "percussion"] [:db/id :lesson/type]}]) <!)
               {[:lesson/by-slug "percussion"] {:db/id 9 :db/table :lesson :lesson/type :lesson.type/video}}))

        (is (= (->> (p/parse env [{[:lesson/by-slug "invalid"] [:db/id :lesson/title]}]) <!)
               {[:lesson/by-slug "invalid"] [:error :row-not-found]}))
        (catch :default e
          (do-report
            {:type :error, :message (.-message e) :actual e})))
      (done))))

(deftest parser-read-route-data
  (async done
    (go
      (is (= (<! (p/parse env [{:route/data [:app/courses]}]))
             {:route/data {:app/courses [{:db/id 4 :db/table :course} {:db/id 7 :db/table :course}]}}))
      (is (= (<! (p/parse env [{:ph/anything [:app/courses]}]))
             {:ph/anything {:app/courses [{:db/id 4 :db/table :course} {:db/id 7 :db/table :course}]}}))
      (done))))

(deftest test-read-lesson-union
  (let [lesson-union {:lesson.type/video    [:lesson/type :lesson/title]
                      :lesson.type/playlist [:lesson/type :lesson/description]
                      :lesson.type/exercise [:lesson/type :lesson/title :url/slug]}]
    (async done
      (go
        (is (= (->> (p/parse env
                             [{[:lesson/by-slug "percussion"]
                               lesson-union}]) <!)
               {[:lesson/by-slug "percussion"]
                {:db/id 9 :db/table :lesson :lesson/title "Percussion" :lesson/type :lesson.type/video}}))
        (is (= (->> (p/parse env
                             [{[:lesson/by-slug "percussion-playlist"]
                               lesson-union}]) <!)
               {[:lesson/by-slug "percussion-playlist"]
                {:db/id 11 :db/table :lesson :lesson/description "" :lesson/type :lesson.type/playlist}}))
        (is (= (->> (p/parse env
                             [{[:lesson/by-slug "tempo-markings"]
                               lesson-union}]) <!)
               {[:lesson/by-slug "tempo-markings"]
                {:db/id 67 :db/table :lesson :lesson/title "Exercise: Tempo Markings Quiz" :lesson/type :lesson.type/exercise
                 :url/slug "tempo-markings"}}))
        (done)))))

(deftest test-read-me
  (async done
    (go
      (is (= (->> (p/parse (assoc env :current-user-id 720) [{:app/me [:db/id]}]) <!
                  :app/me)
             {:db/id 720 :db/table :user}))
      (is (= (->> (p/parse env [{:app/me [:db/id]}]) <!
                  :app/me)
             nil))
      (done))))

(deftest test-read-user-lessons-view
  (async done
    (go
      (try
        (<? (knex/truncate (::ps/db env) "UserVideoView"))
        (<? (ps/save env {:db/table :user-view :user-view/user-id 720}))
        (<? (ps/save env {:db/table :user-view :user-view/user-id 720}))
        (is (= (->> (p/parse (assoc env :current-user-id 720) [{:app/me [:user/lessons-viewed-count]}])
                    <? :app/me)
               {:db/id 720 :db/table :user :user/lessons-viewed-count 2}))
        (catch :default e
          (do-report
            {:type :error, :message (.-message e) :actual e})))
      (done))))
