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
  [:form {:on {:submit [[:dom/prevent-default]
                        [:db/assoc :something/saved [:db/get :something/draft]]]}}
   [:input {:on {:input [[:db/assoc :something/draft :event/target.value]]}}]
   [:button {:type :submit} "Save draft"]])

(defn- display-view [{:something/keys [draft saved dom-node]}]
  [:div
   [:h2 "On display"]
   [:ul
    [:li {:replicant/key "draft"} "Draft: " draft]
    [:li {:replicant/key "saved"} "Saved: " saved]
    (when dom-node
      [:li {:replicant/key "dom-node"} "ID of something dom-node: " [:code (.-id dom-node)]])]])

(defn- something-view []
  [:div#something-something {:replicant/on-mount [[:something/init-something :dom/node]]}
   [:p "Something, something"]])

(defn- main-view [state]
  [:div {:style {:position "relative"}}
   (when (:ui/banner-text state)
     (banner-view state))
   [:h1 "Hello, world!"]
   (edit-view)
   (display-view state)
   (something-view)])

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
        :something/init-something (do 
                                    (js/console.log "Init something, dom-node:" (second enriched-action))
                                    (swap! !state merge {:something/dom-node (second enriched-action)}))
        :db/assoc (apply swap! !state assoc (rest enriched-action))
        :ui/dismiss-banner (swap! !state dissoc :ui/banner-text)
        (prn "Unknown action" enriched-action)))))

(defn ^{:dev/after-load true :export true} start! []
  (render! @!state))

(defn ^:export init! []
  (inspector/inspect "App state" !state)
  (r/set-dispatch! event-handler)
  (add-watch !state :render (fn [_k _r o n]
                              (when (not= o n)
                                (render! @!state))))
  (start!))