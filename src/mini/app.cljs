(ns mini.app
  (:require [replicant.dom :as d]))

(def options
  [["apple" "Apple"]
   ["orange" "Orange"]])

(defn app [{:keys [selected]}]
  [:select {:on {:input [:select]}}
   (for [[id text] (cond-> []
                     (nil? selected) (conj [nil "Select an option"])
                     :always (into options))]
     [:option (cond-> {:value id}
        (= id selected) (assoc :selected true))
      text])])

(defonce !el (atom nil))

(defn ^:export start []
  (set! (.-innerHTML js/document.body) "<div id=\"app\"></div>")

  (reset! !el (js/document.getElementById "app"))

  (let [store (atom {})]

    (add-watch store ::self
               (fn [_ _ _ state]
                 (d/render @!el (app state))))

    (d/set-dispatch!
     (fn [e action]
       (case (first action)
         :select (swap! store assoc :selected (some-> e :replicant/node .-value)))))

    (d/render @!el (app @store))))

(defn ^:export init! []
  (start))

(comment
  (init!)
  :rcf)