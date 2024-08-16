(ns mini.app
  (:require [clojure.walk :as walk]
            [replicant.dom :as r]))

(defonce ^:private !state (atom {}))

(defn- edit-view []
  [:form {:on {:submit [[:dom/prevent-default]
                        [:db/assoc :something/saved [:db/get :something/draft]]]}}
   [:input {:on {:input [[:db/assoc :something/draft :event/target.value]]}}]
   [:button {:type :submit} "Save draft"]])

(defn- display-view [{:something/keys [draft saved]}]
  [:div
   [:p "Draft: " draft]
   [:p "Saved: " saved]])

(defn- main-view [state]
  [:div {:replicant/on-mount [[:something/init-something :dom/node]]
         :style {:position "relative"}}
   [:h1 "Hello, world!"]
   (edit-view)
   (display-view state)])

(defn- render! [state]
  (r/render
   (js/document.getElementById "app")
   (main-view state)))

(defn- enrich-action-from-event [{:replicant/keys [js-event node]} actions]
  (letfn [(process [x]
            (cond
              (keyword? x)
              (case x
                :event/target.value (-> js-event .-target .-value)
                :dom/node node
                x)

              (coll? x) (into (empty x) (map process x))

              :else x))]
    (process actions)))

(defn- enrich-action-from-state [state action]
  (walk/postwalk
   (fn [x]
     (cond
       (and (vector? x)
            (= :db/get (first x))) (get state (second x))
       :else x))
   action))

(defn- event-handler [{:replicant/keys [js-event] :as replicant-data} actions]
  (doseq [action actions]
    (prn "Triggered action" action)
    (let [enriched-action (->> action
                               (enrich-action-from-event replicant-data)
                               (enrich-action-from-state @!state))]
      (prn "Enriched action" enriched-action)
      (case (first enriched-action)
        :dom/prevent-default (.preventDefault js-event)
        :something/init-something (swap! !state merge {:something/dom-node (second enriched-action)})
        :db/assoc (apply swap! !state assoc (rest enriched-action))
        (prn "Unknown action" enriched-action)))))

(defn ^{:dev/after-load true :export true} start! []
  (render! @!state))

(defn ^:export init! []
  (r/set-dispatch! event-handler)
  (add-watch !state :render (fn [_k _r o n]
                              (when (not= o n)
                                (render! @!state))))
  (start!))