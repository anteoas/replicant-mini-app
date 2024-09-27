(ns mini.app
  (:require [clojure.walk :as walk]
            [gadget.inspector :as inspector]
            [reitit.coercion :as rc]
            [reitit.coercion.malli :as rcm]
            [reitit.frontend :as rf]
            [reitit.frontend.easy :as rfe]
            [replicant.core :as r-core]
            [replicant.dom :as r-dom]))

(defonce ^:private !state (atom {:select/selected "1"
                                 :select/things ["1" "2" "3" "4" "5" "6" "7" "8" "9" "10" "11"]}))

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

(comment
  (do (swap! !state assoc :select/selected "2") (render! @!state))
  (do (swap! !state assoc :select/selected "11") (render! @!state) )
  :rcf)

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