(ns daveconservatoire.server.parser-tests
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.test :refer [is are run-tests async testing deftest do-report]]
            [pathom.test :refer [async-test]]
            [common.async :refer [<?]]
            [pathom.sql :as ps]
            [daveconservatoire.server.parser :as p]
            [daveconservatoire.server.test-shared :as ts :refer [env]]
            [nodejs.express :as ex]
            [nodejs.knex :as knex]
            [om.next :as om]))

(defn blank-request [] (js-obj "session" (js-obj)))

(deftest test-create-user
  (async-test
    (<? (knex/raw (::ps/db env) "delete from User where email = ?" ["mary@email.com"]))
    (let [id (<? (p/create-user env {:user/name  "Mary"
                                     :user/email "mary@email.com"}))]
      (is (not (nil? (<? (ps/find-by env {:db/id    id
                                          :db/table :user}))))))))

(deftest test-hit-video-view
  (async-test
    (<? (knex/truncate (::ps/db env) "UserVideoView"))
    (testing "creates hit for empty record"
      (<? (p/hit-video-view env {:user-view/user-id   720
                                 :user-view/lesson-id 5}))
      (is (= (<? (knex/query-count ts/connection [[:from "UserVideoView"]]))
             1)))
    (testing "don't create new entry when last lesson is the same"
      (<? (p/hit-video-view env {:user-view/user-id   720
                                 :user-view/lesson-id 5}))
      (is (= (<? (knex/query-count ts/connection [[:from "UserVideoView"]]))
             1)))
    (testing "create new entry when lesson is different"
      (<? (p/hit-video-view env {:user-view/user-id   720
                                 :user-view/lesson-id 6}))
      (is (= (<? (knex/query-count ts/connection [[:from "UserVideoView"]]))
             2)))))

(deftest test-update-current-user
  (async-test
    (<? (knex/raw ts/connection "update User set biog='' where id = 720" []))
    (testing "creates hit for empty record"
      (<? (p/update-current-user (assoc env
                                   :current-user-id 720)
                                 {:user/about "New Description"}))
      (is (= (-> (knex/query-first ts/connection [[:from "User"] [:where {"id" 720}]])
                 <? (get "biog"))
             "New Description")))))

(deftest test-compute-ex-answer
  (async-test
    (<? (knex/truncate (::ps/db env) "UserExerciseAnswer"))
    (<? (ps/save env {:db/table   :user
                      :db/id      720
                      :user/score 1}))

    (let [req (blank-request)
          env (assoc env :http-request req)]
      (with-redefs [p/current-timestamp (fn [] 123)]
        (testing "saves score on session for unlogged users"
          (<? (p/compute-ex-answer env {:url/slug "bass-clef-reading"}))
          (is (zero? (<? (ps/count env :ex-answer))))
          (is (= (ex/session-get req :guest-tx)
                 [{:db/table                :ex-answer
                   :guest-tx/increase-score 1
                   :db/timestamp            123
                   :ex-answer/lesson-id     53}])))))

    (testing "compute score for logged user"
      (<? (p/compute-ex-answer (assoc env :current-user-id 720)
                               {:url/slug "bass-clef-reading"}))
      (is (= (<? (ps/count env :ex-answer))
             1))
      (is (= (-> (ps/find-by env {:db/table :user :db/id 720})
                 <? :user/score)
             2)))))

(deftest test-compute-ex-answer-master
  (async-test
    (<? (knex/truncate (::ps/db env) "UserExSingleMastery"))
    (<? (ps/save env {:db/table   :user
                      :db/id      720
                      :user/score 1}))

    (let [req (blank-request)
          env (assoc env :http-request req)]
      (with-redefs [p/current-timestamp (fn [] 123)]
        (testing "saves score on session for unlogged users"
          (<? (p/compute-ex-answer-master env {:url/slug "bass-clef-reading"}))
          (is (zero? (<? (ps/count env :ex-mastery))))
          (is (= (ex/session-get req :guest-tx)
                 [{:db/table                :ex-mastery
                   :guest-tx/increase-score 100
                   :db/timestamp            123
                   :ex-mastery/lesson-id    53}])))))

    (testing "compute score for logged user"
      (<? (p/compute-ex-answer-master (assoc env :current-user-id 720)
                                      {:url/slug "bass-clef-reading"}))
      (is (= (<? (ps/count env :ex-mastery))
             1))
      (is (= (-> (ps/find-by env {:db/table :user :db/id 720})
                 <? :user/score)
             101)))

    (testing "can only record mastery once a day for a given exercise"
      (<? (p/compute-ex-answer-master (assoc env :current-user-id 720)
                                      {:url/slug "bass-clef-reading"}))
      (is (= (<? (ps/count env :ex-mastery))
             1))
      (is (= (-> (ps/find-by env {:db/table :user :db/id 720})
                 <? :user/score)
             102))
      (<? (p/compute-ex-answer-master (assoc env :current-user-id 720)
                                      {:url/slug "pitch-1"}))
      (is (= (<? (ps/count env :ex-mastery))
             2)))))

(deftest parse-read-not-found
  (async done
    (go
      (is (= (<? (p/parse {} [:invalid]))
             {:invalid [:error :not-found]}))
      (done))))

(deftest parser-read-courses
  (async-test
    (is (= (<? (p/parse env [:app/courses]))
           {:app/courses [{:db/id 4 :db/table :course} {:db/id 7 :db/table :course}]}))

    (is (= (<? (p/parse env [{:app/courses [:db/id :course/title]}]))
           {:app/courses [{:db/id 4 :course/title "Reading Music" :db/table :course}
                          {:db/id 7 :course/title "Music:  A Beginner's Guide" :db/table :course}]}))

    (is (= (<? (p/parse env [{:app/courses [:db/id :course/title]}]))
           {:app/courses [{:db/id 4 :course/title "Reading Music" :db/table :course}
                          {:db/id 7 :course/title "Music:  A Beginner's Guide" :db/table :course}]}))

    (is (= (<? (p/parse env [{:app/courses [:db/id :course/home-type]}]))
           {:app/courses [{:db/id 4 :course/home-type :course.type/multi-topic :db/table :course}
                          {:db/id 7 :course/home-type :course.type/multi-topic :db/table :course}]}))

    (is (= (<? (p/parse env [{:app/courses {:course.type/multi-topic  [:db/id :course/home-type]
                                            :course.type/single-topic [:db/id :course/title]}}]))
           {:app/courses [{:db/id 4 :course/home-type :course.type/multi-topic :db/table :course}
                          {:db/id 7 :course/home-type :course.type/multi-topic :db/table :course}]}))))

(deftest test-parse-read-lesson-by-slug
  (async-test
    (is (= (->> (p/parse env [{[:lesson/by-slug "percussion"] [:db/id :lesson/title]}]) <?)
           {[:lesson/by-slug "percussion"] {:db/id 9 :db/table :lesson :lesson/title "Percussion"}}))

    (is (= (->> (p/parse env [{[:lesson/by-slug "percussion"] [:db/id :lesson/type]}]) <?)
           {[:lesson/by-slug "percussion"] {:db/id 9 :db/table :lesson :lesson/type :lesson.type/video}}))

    (is (= (->> (p/parse env [{[:lesson/by-slug "invalid"] [:db/id :lesson/title]}]) <?)
           {[:lesson/by-slug "invalid"] [:error :row-not-found]}))))

(deftest parser-read-route-data
  (async-test
    (is (= (<? (p/parse env [{:route/data [:app/courses]}]))
           {:route/data {:app/courses [{:db/id 4 :db/table :course} {:db/id 7 :db/table :course}]}}))
    (is (= (<? (p/parse env [{:ph/anything [:app/courses]}]))
           {:ph/anything {:app/courses [{:db/id 4 :db/table :course} {:db/id 7 :db/table :course}]}}))))

(deftest test-read-lesson-union
  (async-test
    (let [lesson-union {:lesson.type/video    [:lesson/type :lesson/title]
                        :lesson.type/playlist [:lesson/type :lesson/description]
                        :lesson.type/exercise [:lesson/type :lesson/title :url/slug]}]
      (is (= (->> (p/parse env
                           [{[:lesson/by-slug "percussion"]
                             lesson-union}]) <?)
             {[:lesson/by-slug "percussion"]
              {:db/id 9 :db/table :lesson :lesson/title "Percussion" :lesson/type :lesson.type/video}}))
      (is (= (->> (p/parse env
                           [{[:lesson/by-slug "percussion-playlist"]
                             lesson-union}]) <?)
             {[:lesson/by-slug "percussion-playlist"]
              {:db/id 11 :db/table :lesson :lesson/description "" :lesson/type :lesson.type/playlist}}))
      (is (= (->> (p/parse env
                           [{[:lesson/by-slug "tempo-markings"]
                             lesson-union}]) <?)
             {[:lesson/by-slug "tempo-markings"]
              {:db/id    67 :db/table :lesson :lesson/title "Exercise: Tempo Markings Quiz" :lesson/type :lesson.type/exercise
               :url/slug "tempo-markings"}})))))

(deftest test-read-me
  (async-test
    (testing "reading me when logged in"
      (is (= (->> (p/parse (assoc env :current-user-id 720
                                      :http-request (blank-request)) [{:app/me [:db/id]}]) <?
                  :app/me)
             {:db/id 720 :db/table :user})))
    (testing "blank map when user is not signed in"
      (is (= (->> (p/parse (assoc env :http-request (blank-request)) [{:app/me [:db/id]}]) <?
                  :app/me)
             {:db/id -1})))
    (testing "get score for guest user"
      (testing "zero when there is no information"
        (is (= (->> (p/parse (assoc env :http-request (blank-request)) [{:app/me [:user/score]}]) <?
                    :app/me)
               {:user/score 0})))
      (testing "sum the scores"
        (let [req (js-obj "session" (js-obj ":guest-tx" (pr-str [{:guest-tx/increase-score 4}
                                                                 {:guest-tx/increase-score 3}])))]
          (is (= (->> (p/parse (assoc env :http-request req) [{:app/me [:user/score]}]) <?
                      :app/me)
                 {:user/score 7})))))))

(deftest test-read-user-lessons-view
  (async-test
    (<? (knex/truncate (::ps/db env) "UserVideoView"))
    (<? (ps/save env {:db/table :user-view :user-view/user-id 720}))
    (<? (ps/save env {:db/table :user-view :user-view/user-id 720}))
    (is (= (->> (p/parse (assoc env :current-user-id 720) [{:app/me [:user/lessons-viewed-count]}])
                <? :app/me)
           {:db/id 720 :db/table :user :user/lessons-viewed-count 2}))))

(deftest test-read-user-ex-answered
  (async-test
    (<? (knex/truncate (::ps/db env) "UserExerciseAnswer"))
    (<? (knex/truncate (::ps/db env) "UserExSingleMastery"))
    (<? (ps/save env {:db/table            :ex-answer
                      :ex-answer/user-id   720
                      :ex-answer/lesson-id 120}))
    (<? (ps/save env {:db/table            :ex-answer
                      :ex-answer/user-id   720
                      :ex-answer/lesson-id 120}))
    (<? (ps/save env {:db/table             :ex-mastery
                      :ex-mastery/user-id   720
                      :ex-mastery/lesson-id 121}))

    (is (= (->> (p/parse (assoc env :current-user-id 720) [{:app/me [:user/ex-answer-count]}])
                <? :app/me)
           {:db/id 720 :db/table :user :user/ex-answer-count 3}))))

(deftest test-read-user-activity
  (async-test
    (<? (knex/truncate (::ps/db env) "UserVideoView"))
    (<? (knex/truncate (::ps/db env) "UserExerciseAnswer"))
    (<? (knex/truncate (::ps/db env) "UserExSingleMastery"))
    (<? (ps/save env {:db/table :user-view :user-view/user-id 720}))
    (<? (ps/save env {:db/table            :ex-answer
                      :ex-answer/user-id   720
                      :ex-answer/lesson-id 120}))
    (<? (ps/save env {:db/table             :ex-mastery
                      :ex-mastery/user-id   720
                      :ex-mastery/lesson-id 121}))

    (is (= (->> (p/parse (assoc env :current-user-id 720) [{:app/me [{:user/activity [:db/timestamp :user-view/user-id]}]}])
                <? :app/me)
           {:db/id         720 :db/table :user
            :user/activity [{:db/table :user-view, :db/timestamp 0, :db/id 1, :user-view/user-id 720}
                            {:db/table :ex-answer, :db/timestamp 0, :db/id 1}
                            {:db/table :ex-mastery, :db/timestamp 0, :db/id 1}]}))))

(deftest test-read-lesson-view-state
  (async-test
    (<? (knex/truncate (::ps/db env) "UserExSingleMastery"))
    (<? (knex/truncate (::ps/db env) "UserVideoView"))
    (<? (ps/save env {:db/table            :user-view
                      :user-view/user-id   720
                      :user-view/lesson-id 9}))

    (testing "nil when user is not authenticated"
      (is (= (->> (p/parse env
                           [{[:lesson/by-slug "percussion"]
                             [:lesson/view-state]}]) <?)
             {[:lesson/by-slug "percussion"] {:db/table :lesson
                                              :db/id    9}})))

    (testing "video viewed state"
      (testing "nil when user didn't saw the video"
        (is (= (->> (p/parse (assoc env :current-user-id 720)
                             [{[:lesson/by-slug "tempo-markings"]
                               [:lesson/view-state]}]) <?)
               {[:lesson/by-slug "tempo-markings"] {:db/table          :lesson
                                                    :db/id             67
                                                    :lesson/view-state nil}})))

      (testing "viewed when the video was watched"
        (is (= (->> (p/parse (assoc env :current-user-id 720)
                             [{[:lesson/by-slug "percussion"]
                               [:lesson/view-state]}]) <?)
               {[:lesson/by-slug "percussion"] {:db/table          :lesson
                                                :db/id             9
                                                :lesson/view-state :lesson.view-state/viewed}}))))

    (<? (knex/truncate (::ps/db env) "UserExerciseAnswer"))
    (<? (ps/save env {:db/table            :ex-answer
                      :ex-answer/user-id   720
                      :ex-answer/lesson-id 120}))

    (testing "exercise started"
      (is (= (->> (p/parse (assoc env :current-user-id 720)
                           [{[:lesson/by-slug "pitch-1"]
                             [:lesson/view-state]}]) <?)
             {[:lesson/by-slug "pitch-1"] {:db/table          :lesson
                                           :db/id             120
                                           :lesson/view-state :lesson.view-state/started}})))

    (<? (knex/truncate (::ps/db env) "UserExSingleMastery"))
    (<? (ps/save env {:db/table             :ex-mastery
                      :ex-mastery/user-id   720
                      :ex-mastery/lesson-id 121}))

    (testing "exercise mastered"
      (is (= (->> (p/parse (assoc env :current-user-id 720)
                           [{[:lesson/by-slug "pitch-2"]
                             [:lesson/view-state]}]) <?)
             {[:lesson/by-slug "pitch-2"] {:db/table          :lesson
                                           :db/id             121
                                           :lesson/view-state :lesson.view-state/mastered}})))))

(deftest test-read-lesson-prev
  (async-test
    (testing "fetchs the previous item"
      (is (= (->> (p/parse env
                           [{[:lesson/by-slug "pitch-1"]
                             [{:lesson/prev [:url/slug]}]}]) <?)
             {[:lesson/by-slug "pitch-1"] {:db/table    :lesson
                                           :db/id       120
                                           :lesson/prev {:db/id    88 :db/table :lesson
                                                         :url/slug "pitch-and-octaves"}}})))

    (testing "return nil when there is no previous item"
      (is (= (->> (p/parse env
                           [{[:lesson/by-slug "pitch-and-octaves"]
                             [{:lesson/prev [:url/slug]}]}]) <?)
             {[:lesson/by-slug "pitch-and-octaves"] {:db/table    :lesson
                                                     :db/id       88
                                                     :lesson/prev nil}})))))

(deftest test-read-lesson-next
  (async-test
    (testing "fetchs the next item"
      (is (= (->> (p/parse env
                           [{[:lesson/by-slug "pitch-and-octaves"]
                             [{:lesson/next [:url/slug]}]}]) <?)
             {[:lesson/by-slug "pitch-and-octaves"] {:db/table    :lesson
                                                     :db/id       88
                                                     :lesson/next {:db/id    42 :db/table :lesson
                                                                   :url/slug "pitch-playlist"}}})))

    (testing "return nil when there is no next item"
      (is (= (->> (p/parse env
                           [{[:lesson/by-slug "introducing-melody"]
                             [{:lesson/next [:url/slug]}]}]) <?)
             {[:lesson/by-slug "introducing-melody"] {:db/table    :lesson
                                                      :db/id       107
                                                      :lesson/next nil}})))))

(deftest test-read-topic-started?
  (async-test
    (<? (knex/truncate (::ps/db env) "UserVideoView"))
    (<? (knex/truncate (::ps/db env) "UserExerciseAnswer"))
    (<? (ps/save env {:db/table            :user-view
                      :user-view/user-id   720
                      :user-view/lesson-id 9}))
    (<? (ps/save env {:db/table            :ex-answer
                      :ex-answer/user-id   720
                      :ex-answer/lesson-id 120}))
    (testing "false when not signed in"
      (is (= (->> (ps/sql-first-node (assoc env ::ps/table :topic
                                                :ast {:query [:topic/started?]})
                                     [[:where {"urltitle" "instruments"}]]) <?)
             {:topic/started? false, :db/table :topic, :db/id 13})))
    (testing "true when user did watched lessons on that topic"
      (is (= (->> (ps/sql-first-node (assoc env ::ps/table :topic
                                                :current-user-id 720
                                                :ast {:query [:topic/started?]})
                                     [[:where {"urltitle" "instruments"}]]) <?)
             {:topic/started? true, :db/table :topic, :db/id 13})))
    (testing "true when user did an exercise on that topic"
      (is (= (->> (ps/sql-first-node (assoc env ::ps/table :topic
                                                :current-user-id 720
                                                :ast {:query [:topic/started?]})
                                     [[:where {"urltitle" "pitch"}]]) <?)
             {:topic/started? true, :db/table :topic, :db/id 3})))
    (testing "false when user didn't watched lessons on the topic"
      (is (= (->> (ps/sql-first-node (assoc env ::ps/table :topic
                                                :current-user-id 720
                                                :ast {:query [:topic/started?]})
                                     [[:where {"urltitle" "texture"}]]) <?)
             {:topic/started? false, :db/table :topic, :db/id 16})))))

(deftest test-consume-guest-tx
  (async-test
    (<? (knex/truncate (::ps/db env) "UserExerciseAnswer"))
    (<? (ps/save env {:db/table   :user
                      :db/id      720
                      :user/score 1}))

    (let [req (blank-request)
          guest-tx [{:db/table                :ex-answer
                     :guest-tx/increase-score 3
                     :db/timestamp            123
                     :ex-answer/lesson-id     53}]
          env (assoc env :http-request req
                         :current-user-id 720)]
      (ex/session-set! req :guest-tx guest-tx)

      (<? (p/consume-guest-tx env))

      (testing "the score is increased"
        (is (= (-> (ps/find-by env {:db/table :user :db/id 720})
                   <? :user/score)
               4)))

      (testing "the records are created"
        (is (= (<? (ps/find-by env {:db/table            :ex-answer
                                    :ex-answer/lesson-id 53}))
               {:db/id 1, :ex-answer/user-id 720, :ex-answer/lesson-id 53, :db/timestamp 123, :db/table :ex-answer})))

      (testing "the guest-tx are cleaned from the session"
        (is (= (ex/session-get req :guest-tx)
               []))))))
