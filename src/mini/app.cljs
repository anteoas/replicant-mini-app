(ns mini.app
  (:require [clojure.string :as string]
            [clojure.walk :as walk]
            [cognitect.transit :as t]
            [gadget.inspector :as inspector]
            [replicant.dom :as r]))

(def default-db {:app/todo-items []})

(defonce ^:private !state (atom nil))

(def storage-key "replicant-todomvc")

(def persist-keys [:app/todo-items :app/mark-all-state])

(defn load-persisted! []
  (or (->> (.getItem js/localStorage storage-key)
           (t/read (t/reader :json)))
      default-db))

(defn persist! [state]
  (.setItem js/localStorage storage-key (t/write (t/writer :json) (select-keys state persist-keys))))

(defn- maybe-add [coll s]
  (let [trimmed (string/trim s)]
    (if (string/blank? trimmed)
      coll
      (conj coll {:item/title trimmed
                  :item/completed false
                  :item/id (random-uuid)}))))

(defn remove-index [i v]
  (vec (concat (subvec v 0 i) (subvec v (inc i)))))

(defn- add-view []
  [:form {:on {:submit [[:dom/prevent-default]
                        [:db/update :app/todo-items maybe-add [:db/get :item/draft]]
                        [:db/assoc :item/draft ""]
                        [:dom/set-input-text [:db/get :dom/draft-input-element] ""]]}}
   [:input.new-todo {:replicant/on-mount [[:db/assoc :dom/draft-input-element :dom/node]]
                     :type :text
                     :autofocus true
                     :placeholder "What needs to be done?"
                     :on {:input [[:db/assoc :item/draft :event/target.value]]}}]])

(defn- main-view [{:keys [app/todo-items] :as state}]
  [:div.main
   [:input#toggle-all.toggle-all {:type :checkbox
                                  :checked (:app/mark-all-state state)
                                  :on {:change [[:db/assoc :app/mark-all-state :event/target.checked]
                                                [:app/mark-all-items-as todo-items :event/target.checked]]}}]
   [:label {:for "toggle-all"}
    "Mark all as complete"]
   [:ul.todo-list
    (map-indexed (fn [i item]
                   [:li {:replicant/key (:item/id item)}
                    [:div.view
                     [:input.toggle {:type :checkbox
                                     :checked (:item/completed item)
                                     :on {:change [[:db/update-in [:app/todo-items i :item/completed] not]
                                                   [:app/set-mark-all-state]]}}]
                     [:label (:item/title item)]
                     [:button.destroy {:on {:click [[:db/update :app/todo-items (partial remove-index i)]
                                                    [:app/set-mark-all-state]]}}]]])
                 todo-items)]])

(defn footer-view [{:keys [app/todo-items]}]
  (let [active-count (count (remove :item/completed todo-items))]
    [:footer.footer
     [:span.todo-count
      [:strong active-count]
      (str " "
           (condp = active-count 1 "item" "items")
           " left")]
     [:ul.filters
      [:li [:a {:href "#/"} "All"]]
      [:li [:a {:href "#/active"} "Active"]]
      [:li [:a {:href "#/completed"} "Completed"]]]
     (when (seq (filter :item/completed todo-items))
       [:button.clear-completed {:on {:click [[:db/dissoc :app/todo-items]]}}
        "Clear completed"])]))

(defn- todoapp-view [{:keys [app/todo-items] :as state}]
  [:section.todoapp
   [:header.header
    [:h1 "todos"]
    (add-view)]
   (when (seq todo-items)
     (list
      (main-view state)
      (footer-view state)))])

(defn js-get-in [o path]
  (reduce (fn [acc k]
            (unchecked-get acc k))
          o
          path))

(defn- enrich-action-from-event [{:replicant/keys [js-event node]} actions]
  (walk/postwalk
   (fn [x]
     (if (keyword? x)
       (cond (= "event" (namespace x)) (let [path (string/split (name x) #"\.")]
                                         (js-get-in js-event path))
             (= :dom/node x) node
             :else x)
       x))
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
  (r/render
   (js/document.getElementById "app")
   (todoapp-view state)))

(defn get-mark-all-as-state [items]
  (let [as-state (if (every? :item/completed items)
                   false
                   (every? :item/completed (filter :item/completed items)))]
    (prn "Mark all as state" as-state)
    as-state))

(defn mark-items-as [items completed?]
  (println "Marking items as" completed?)
  (mapv (fn [item]
          (assoc item :item/completed completed?))
        items))

(defn- event-handler [{:replicant/keys [^js js-event] :as replicant-data} actions]
  (doseq [action actions]
    (prn "Triggered action" action)
    (let [enriched-action (->> action
                               (enrich-action-from-event replicant-data)
                               (enrich-action-from-state @!state))
          [action-name & args] enriched-action]
      (prn "Enriched action" enriched-action)
      (case action-name
        :app/mark-all-items-as (swap! !state assoc :app/todo-items (mark-items-as (first args) (second args)))
        :app/set-mark-all-state (swap! !state assoc :app/mark-all-state (not (get-mark-all-as-state (:app/todo-items @!state))))
        :dom/prevent-default (.preventDefault js-event)
        :db/assoc (apply swap! !state assoc args)
        :db/dissoc (apply swap! !state dissoc args)
        :db/update (apply swap! !state update (first args) (rest args))
        :db/update-in (apply swap! !state update-in (first args) (rest args))
        :dom/set-input-text (set! (.-value (first args)) (second args))
        ;:dom/focus-element (.focus (first args))
        )
      (persist! @!state)))
  (render! @!state))

(defn ^{:dev/after-load true :export true} start! []
  (render! @!state))

(defn ^:export init! []
  (reset! !state (load-persisted!))
  (inspector/inspect "App state" !state)
  (r/set-dispatch! event-handler)
  (start!))