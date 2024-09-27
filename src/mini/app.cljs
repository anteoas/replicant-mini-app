(ns mini.app
  (:require [clojure.walk :as walk]
            [gadget.inspector :as inspector]
            [reitit.coercion :as rc]
            [reitit.coercion.malli :as rcm]
            [reitit.frontend :as rf]
            [reitit.frontend.easy :as rfe]
            [replicant.core :as r-core]
            [replicant.dom :as r-dom]))

;; Repro for strange issue with reitit + replicant
;; I am probably holding one or both of these wrong
;;
;; Repro:
;; 1. Start the repl (jack in, shadow-cljs, start and connect the :app build)
;; 2. Open http://localhost:8787 in the browser (Chrome)
;; 3. Confirm that "Select..." is selected (inspect in dev tools too)
;; 4. Select "11" (say)
;; 5. Confirm that "11" is selected (also check the browser url)
;; 6. Go back in the browser
;; 7. Confirm that "Select..." is selected
;; 8. Go forward in the browser
;; Expected: "11" is selected
;;           in the elements inspector "11" is selected
;;           the browser url is "http://localhost:8787/#/select/11"
;; Actual: "Select..." is selected
;;         in the elements inspector "11" is selected
;;         the browser url is "http://localhost:8787/#/select/11"
;;
;; Things check out in Safari
;;
;; Another Chrome specific strangeness:
;; 1. With the repl started
;; 2. Open http://localhost:8787 in a new tab
;; 3. Confirm that "Select..." is selected
;; 4. Evaluate the dispatches in the below Rich comment form
;; 5. With each dispatch confirm that the browser do the right things
;;    (UI, url, elements inspector)
;; 6. Confirm that now "7" is selected
;; 7. Go back in the browser
;; Expected: "11" is selected
;; Actual: The browser loads its new tab page
;; 8. Go forward in the browser
;; 9. Confirm that now "7" is selected
;; 10. Go back in the browser
;; Expected: "11" is selected
;; Actual: The browser loads its new tab page
;; 11. Go forward in the browser
;; 12. Confirm that now "7" is selected
;; 13. Reload the page
;; 14. Confirm that now "7" is selected
;; 15. Go back in the browser
;; Expected: (I don't know what you now expect 😂)
;; Actual: "11" is selected
;;         and navigation back and forth in the history works fine 🤯
;;
;; In Safari the browser behaves as expected


(comment
  (replicant-dispatch! nil [[:router/navigate-to :route/select {:path-params {:id "2"}}]])
  ; (rfe/navigate :route/select {:path-params {:id "2"}}) ; equivalent to ^
  (replicant-dispatch! nil [[:router/navigate-to :route/select {:path-params {:id "11"}}]])
  (replicant-dispatch! nil [[:router/navigate-to :route/select {:path-params {:id "7"}}]])


  (do (swap! !state assoc :select/selected "2") (render! @!state))
  (do (swap! !state assoc :select/selected "11") (render! @!state))
  :rcf)

(defonce ^:private !state (atom {:select/things ["1" "2" "3" "4" "5" "6" "7" "8" "9" "10" "11"]}))

(defonce !el (atom nil))

(def ^:private routes [["/"
                        {:name :route/home}]
                       ["/select/:id"
                        {:name :route/select
                         :coercion rcm/coercion
                         :parameters {:path [:map [:id :int]]}}]])

(defn start-router! [dispatch!]
  (rfe/start! (rf/router routes)
              (fn [m]
                (dispatch! nil [[:router/dispatch m]]))
              {:use-fragment true
               :compile rc/compile-request-coercers}))

(defn replicant-dispatch!
  "Dispatch event data outside of Replicant actions"
  ;; TODO: Reimplement with public API once Replicant has one
  [e data]
  (let [el @!el]
    (if (and r-core/*dispatch* el)
      (if (get-in @r-dom/state [el :rendering?])
        (js/requestAnimationFrame #(r-core/*dispatch* e data))
        (r-core/*dispatch* e data))
      (throw (js/Error. "Cannot dispatch custom event data without a global event handler. Call replicant.core/set-dispatch!")))))

(defn- main-view [{:keys [select/selected]}]
  [:div
   [:h2 "Edit"]
   [:select {:on {:change [[:router/navigate-to :route/select {:path-params {:id :event/target.value}}]]
                  #_[[:db/assoc :select/selected :event/target.value]]}}
    [:option {:replicant/key "nothing"
              :selected (or (nil? selected)
                            (= "nothing" selected))}
     "Select..."]
    (for [thing (:select/things @!state)]
      ^{:key thing}
      [:option {:replicant/key thing
                :value thing
                :selected (= thing selected)}
       thing])]])

(defn- enrich-action-from-event [{:replicant/keys [js-event node]} actions]
  (walk/postwalk
   (fn [x]
     (cond
       (keyword? x)
       (case x
         :event/target.value (-> js-event .-target .-value)
         :dom/node node
         x)
       :else x))
   actions))

(defn- enrich-action-from-state [state action]
  (walk/postwalk
   (fn [x]
     (cond
       (and (vector? x)
            (= :db/get (first x))) (get state (second x))
       :else x))
   action))

(defn- render! [state]
  (r-dom/render
   @!el
   (main-view state)))

(defn- event-handler [replicant-data actions]
  (doseq [action actions]
    (prn "Triggered action" action)
    (let [enriched-action (->> action
                               (enrich-action-from-event replicant-data)
                               (enrich-action-from-state @!state))
          [action-name & args] enriched-action]
      (prn "Enriched action" enriched-action)
      (case action-name
        :db/assoc (apply swap! !state assoc args)
        :router/dispatch (let [{:keys [path-params]} (first args)
                               {:keys [id]} path-params]
                           (swap! !state assoc :select/selected id))
        :router/navigate-to (rfe/navigate (first args) (second args))
        (prn "Unknown action" action))))
  (render! @!state))

(defn ^{:dev/after-load true :export true} start! []
  (render! @!state))

(defn ^:export init! []
  (reset! !el (js/document.getElementById "app"))
  (inspector/inspect "App state" !state)
  (r-dom/set-dispatch! event-handler)
  (start-router! replicant-dispatch!)
  (start!))