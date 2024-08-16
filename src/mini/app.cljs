(ns mini.app
  (:require [replicant.dom :as r]))

(defonce ^:private !state (atom {:ui/splash? true}))

(defn- sub-view [{:something/keys [something]}]
  [:div {:replicant/on-mount [[:something/init-something]]}
   (when something
     [:p "Something: " something])])

(defn- add-button []
  [:button {:on {:click [[:db/assoc :something/something :something]]}} "Add something"])

(defn- main-view [state]
  [:div
   (sub-view state)
   (add-button)])

(defn- render! [state]
  (r/render
   (js/document.getElementById "app")
   (main-view state)))

(defn- event-handler [_replicant-data actions]
  (doseq [action actions]
    (prn "Triggered action" action)
    (case (first action)
      :something/init-something (reset! !state {:something/initialized true})
      :db/assoc (apply swap! !state assoc (rest action))
      (prn "Unknown action" action)))
  (render! @!state))

(defn ^{:dev/after-load true :export true} start! []
  (render! @!state))

(defn ^:export init! []
  (r/set-dispatch! event-handler)
  (start!))