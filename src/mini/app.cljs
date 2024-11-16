(ns mini.app
  (:require [clojure.string :as string]
            [clojure.walk :as walk]
            [cognitect.transit :as t]
            [gadget.inspector :as inspector]
            [replicant.core :as r-core]
            [replicant.dom :as r-dom]
            [reitit.frontend :as rf]
            [reitit.frontend.easy :as rfe]))

(def default-db {:app/todo-items []
                 :app/el nil})

(defonce ^:private !state (atom nil))

(defn- replicant-dispatch!
  "Dispatch event data outside of Replicant actions"
  ;; TODO: Reimplement with public API once Replicant has one
  [e data]
  (let [el (:app/el @!state)]
    (if (and r-core/*dispatch* el)
      (if (get-in @r-dom/state [el :rendering?])
        (js/requestAnimationFrame #(r-core/*dispatch* e data))
        (r-core/*dispatch* e data))
      (throw (js/Error. "Cannot dispatch custom event data without a global event handler. Call replicant.core/set-dispatch!")))))

(def ^:private routes [["/" {:name :route/home}]
                       ["/active" {:name :route/active}]
                       ["/completed" {:name :route/completed}]])

(defn- start-router! [dispatch!]
  (rfe/start! (rf/router routes)
              (fn [m]
                (dispatch! nil [[:router/dispatch m]]))
              {:use-fragment true}))

(defn- route-dispatch! [{:keys [data]}]
  (let [route (:name data)]
    (case route
      :route/home (replicant-dispatch! nil [[:db/assoc :app/item-filter :filter/all]])
      :route/active (replicant-dispatch! nil [[:db/assoc :app/item-filter :filter/active]])
      :route/completed (replicant-dispatch! nil [[:db/assoc :app/item-filter :filter/completed]]))))

(def storage-key "replicant-todomvc")

(def persist-keys [:app/todo-items
                   :add/draft
                   :app/mark-all-state])

(defn- load-persisted! []
  (or (->> (.getItem js/localStorage storage-key)
           (t/read (t/reader :json)))
      default-db))

(defn- persist! [state]
  (.setItem js/localStorage storage-key (t/write (t/writer :json) (select-keys state persist-keys))))

(defn- maybe-add [coll s]
  (let [trimmed (string/trim s)]
    (if (string/blank? trimmed)
      coll
      (conj coll {:item/title trimmed
                  :item/completed false
                  :item/id (random-uuid)}))))

(defn- remove-index [i v]
  (vec (concat (subvec v 0 i) (subvec v (inc i)))))

(defn- add-view [{:keys [add/draft]}]
  [:form {:on {:submit [[:dom/prevent-default]
                        [:db/update :app/todo-items maybe-add draft]
                        [:db/assoc :add/draft ""]
                        [:dom/set-input-text [:db/get :add/draft-input-element] ""]]}}
   [:input.new-todo {:replicant/on-mount [[:db/assoc :add/draft-input-element :dom/node]]
                     :type :text
                     :autofocus true
                     :placeholder "What needs to be done?"
                     :on {:input [[:db/assoc :add/draft :event/target.value]]}}]])

(defn- edit-view [{:keys [index item edit/editing-item-index edit/draft edit/keyup-code]}]
  (when (and (= index editing-item-index)
             (not= "Escape" keyup-code))
    [:form {:replicant/key (:item/id item)
            :replicant/on-unmount [[:edit/end-editing (string/trim draft) index]]
            :on {:submit (into [[:dom/prevent-default]
                                [:db/dissoc :edit/editing-item-index]])}}
     [:input.edit {:replicant/on-mount [[:dom/focus-element :dom/node]]
                   :value (:item/title item)
                   :on {:blur [[:db/dissoc :edit/editing-item-index]]
                        :keyup [[:db/assoc :edit/keyup-code :event/code]]
                        :input [[:db/assoc :edit/draft :event/target.value]]}}]]))

(defn- todo-list-view [{:keys [app/todo-items edit/editing-item-index app/item-filter]
                        :as state}]
  [:ul.todo-list
   (map-indexed (fn [index item]
                  [:li (cond-> {:replicant/key (:item/id item)
                                :class (when (= index editing-item-index) ["editing"])
                                :on {:dblclick [[:db/assoc :edit/editing-item-index index]
                                                [:db/assoc :edit/draft (:item/title item)]]}}
                         (and (= :filter/active item-filter)
                              (:item/completed item)) (assoc :style {:display "none"})
                         (and (= :filter/completed item-filter)
                              (not (:item/completed item))) (assoc :style {:display "none"}))
                   [:div.view
                    [:input.toggle {:type :checkbox
                                    :checked (:item/completed item)
                                    :on {:change [[:db/update-in [:app/todo-items index :item/completed] not]
                                                  [:app/set-mark-all-state]]}}]
                    [:label (:item/title item)]
                    [:button.destroy {:on {:click [[:db/update :app/todo-items (partial remove-index index)]
                                                   [:app/set-mark-all-state]]}}]]
                   (edit-view (merge state {:index index
                                            :item item}))])
                todo-items)])

(defn- main-view [{:keys [app/todo-items] :as state}]
  [:div.main
   [:input#toggle-all.toggle-all {:type :checkbox
                                  :checked (:app/mark-all-state state)
                                  :on {:change [[:db/assoc :app/mark-all-state :event/target.checked]
                                                [:app/mark-all-items-as todo-items :event/target.checked]]}}]
   [:label {:for "toggle-all"}
    "Mark all as complete"]
   (todo-list-view state)])

(defn- items-footer-view [{:keys [app/todo-items]}]
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
       [:button.clear-completed {:on {:click [[:db/update :app/todo-items (partial filterv (complement :item/completed))]]}}
        "Clear completed"])]))

(defn- footer-view []
  [:footer.info
   [:p "Double-click to edit a todo"]
   [:p "Created by "
    [:a {:href "https://github.com/PEZ"} "Peter Strömberg"]]
   [:p "Part of "
    [:a {:href "https://todomvc.com"} "TodoMVC"]]])

(defn- todoapp-view [{:keys [app/todo-items] :as state}]
  [:div
   [:section.todoapp
    [:header.header
     [:h1 "todos"]
     (add-view state)]
    (when (seq todo-items)
      (list
       (main-view state)
       (items-footer-view state)))]
   (footer-view)])

(defn- get-mark-all-as-state [items]
  (let [as-state (if (every? :item/completed items)
                   false
                   (every? :item/completed (filter :item/completed items)))]
    (prn "Mark all as state" as-state)
    as-state))

(defn- mark-items-as [items completed?]
  (println "Marking items as" completed?)
  (mapv (fn [item]
          (assoc item :item/completed completed?))
        items))

(defn- end-editing [state keyup-code draft index]
  (let [save-edit? (and (not (string/blank? draft))
                        (not= "Escape" keyup-code))
        delete-item? (string/blank? draft)]
    (cond-> state
      save-edit? (assoc-in [:app/todo-items index :item/title] draft)
      delete-item? (update :app/todo-items (partial remove-index index))
      delete-item? (assoc :app/mark-all-state (not (get-mark-all-as-state (:app/todo-items state))))
      :always (dissoc :edit/editing-item-index :edit/keyup-code))))

(defn- js-get-in [o path]
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
  (r-dom/render
   (:app/el state)
   (todoapp-view state)))

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
        :console/debug (apply (comp js/console.debug prn) args)
        :db/assoc (apply swap! !state assoc args)
        :db/assoc-in (apply swap! !state assoc-in args)
        :db/dissoc (apply swap! !state dissoc args)
        :db/update (apply swap! !state update args)
        :db/update-in (apply swap! !state update-in args)
        :dom/focus-element (.focus (first args))
        :dom/prevent-default (.preventDefault js-event)
        :dom/set-input-text (set! (.-value (first args)) (second args))
        :edit/end-editing (apply swap! !state end-editing (:edit/keyup-code @!state) args)
        :router/dispatch (route-dispatch! (first args)))
      (persist! @!state)))
  (render! @!state))

(defn ^{:dev/after-load true :export true} start! []
  (render! @!state))

(defn ^:export init! []
  (reset! !state (load-persisted!))
  (swap! !state assoc :app/el (js/document.getElementById "app"))
  (inspector/inspect "App state" !state)
  (r-dom/set-dispatch! event-handler)
  (start-router! replicant-dispatch!)
  (start!))