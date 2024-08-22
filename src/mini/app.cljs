(ns mini.app
  (:require [clojure.walk :as walk]
            [gadget.inspector :as inspector]
            [replicant.dom :as r]))

(defonce ^:private !state (atom {:ui/banner-text "An annoying banner"}))

(defn banner-view [{:ui/keys [banner-text]}]
  [:div#banner {:style {:top 0
                        :transition "top 0.25s"}
                :replicant/mounting {:style {:top "-100px"}}
                :replicant/unmounting {:style {:top "-100px"}}}
   [:p banner-text]
   [:button {:on {:click [[:ui/dismiss-banner]]}} "Dismiss"]])

(defn- edit-view []
  [:div
   [:form {:on {:submit [[:dom/prevent-default]
                         [:db/assoc :something/saved [:db/get :something/draft]]]}}
    [:input#draft {:replicant/on-mount [[:db/assoc :something/draft-input-element :dom/node]]
                   :on {:input [[:db/assoc :something/draft :event/target.value]]}}]
    [:button {:type :submit} "Save draft"]]
   [:button {:on {:click [[:db/assoc :something/draft ""]
                          [:dom/set-input-text [:db/get :something/draft-input-element] ""]
                          [:dom/focus-element [:db/get :something/draft-input-element]]]}}
    "Clear draft"]])

(defn- display-view [{:something/keys [draft saved]}]
  [:div
   [:h2 "On display"]
   [:ul
    [:li {:replicant/key "draft"} "Draft: " draft]
    [:li {:replicant/key "saved"} "Saved: " saved]]])

(defn- main-view [state]
  [:div {:style {:position "relative"}}
   (when (:ui/banner-text state)
     (banner-view state))
   [:h1 "A tiny (and silly) Replicant example"]
   (edit-view)
   (display-view state)])

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

(defn- render! [state]
  (r/render
   (js/document.getElementById "app")
   (main-view state)))

(defn- event-handler [{:replicant/keys [^js js-event] :as replicant-data} actions]
  (doseq [action actions]
    (prn "Triggered action" action)
    (let [enriched-action (->> action
                               (enrich-action-from-event replicant-data)
                               (enrich-action-from-state @!state))
          [action-name & args] enriched-action]
      (prn "Enriched action" enriched-action)
      (case action-name
        :dom/prevent-default (.preventDefault js-event)
        :something/init-something (do 
                                    (js/console.debug "Init something, dom-node:" (first args))
                                    (swap! !state merge {:something/dom-node (first args)}))
        :db/assoc (apply swap! !state assoc (rest enriched-action))
        :ui/dismiss-banner (swap! !state dissoc :ui/banner-text)
        :dom/set-input-text (set! (.-value (first args)) (second args))
        :dom/focus-element (.focus (first args))
        (prn "Unknown action" action))))
  (render! @!state))

(defn ^{:dev/after-load true :export true} start! []
  (render! @!state))

(defn ^:export init! []
  (inspector/inspect "App state" !state)
  (r/set-dispatch! event-handler)
  (start!))