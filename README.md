# replicant-mini-app

A tiny example of a [Replicant](https://github.com/cjohansen/replicant) app.

This is an example of how to wire up a Replicant app, with a focus on keeping the views as pure data. We can do this because replicant supports pure data dom event handlers and vdom lifecycle hooks. 

**Replicant**
: A native [ClojureScript](https://clojurescript.org) virtual DOM renderer - render hiccup directly

## Running the app

We're using [shadow-cljs](https://github.com/thheller/shadow-cljs) to build the app. Clojure editors like [Calva](https://calva.io) and [CIDER](https://cider.mx/) will let you quickly start the app and connect you to the REPL. You can also just run it without installing anything, by using npx:

```sh
npx shadow-cljs watch :app  
```

Once built, you can access the app at http://localhost:8787

## Reading the code

All the code is in [src/mini/app.cljs](src/mini/app.cljs). It is largely void of any comments, to make it easier to read. Here are some pointers to get you started:

The app uses vectors for the event and lifecycle hook handlers. Each _event_/_hook_ vector holds zero or more _actions_, which also are vectors. The first element of an _action_ vector is the action key/identifier, and the rest of the elements are the arguments to the action. See the `event-handler` function for how we dispatch on the action keys.

Here's the `edit-view` function from the app:

```clojure
(defn- edit-view []
  [:form {:on {:submit [[:dom/prevent-default]
                        [:db/assoc :something/saved [:db/get :something/draft]]]}}
   [:input#draft {:on {:input [[:db/assoc :something/draft :event/target.value]]}}]
   [:button {:type :submit} "Save draft"]])
```

> Note that there is nothing effectful going on here, nothing is written to any database, the DOM is not manipulated or inspected. That's all taken care of by Replicant and our event handler.

Typing in the `input` element, will fire an event with one action:

1.  `[:db/assoc :something/draft :event/target.value]`

The event handler has access to a reference to the atom we use to store the state of the app. You can probably guess what the action does. But of course we are not `assoc`iating `:event/target.value` into the database. We will first replace it with the actual value of the input field. We can do this because Replicant will provide the event handler with some data of the event, includingthe DOM event object. We refer to this replacing as _enriching_ the action. See the `enrich-action-from-event` function.

Enrichment also involves replacing certain forms in the actions with data from the database (using the `enrich-action-from-state` function). This is in play when submitting the form, which will fire an event of two actions:

1. `[:dom/prevent-default]`
2. `[:db/assoc :something/saved [:db/get :something/draft]]`

The first action will be replaced with a `(.preventDefault js-event)` call . It's the section action that has the state enrichment. The form `[:db/get :something/draft]` will be replaced with the value of the database at the key `:something/draft`.

We can take a look at the console log of this example run:

![Example app screenshot](app-screenshot.png)

In the log we can find the result of:

1. Typing "a" in the input field
   ```
   "Triggered action" [:db/assoc :something/draft :event/target.value]
   "Enriched action" [:db/assoc :something/draft "a"]
   ```
2. Submitting the form 
   ```
   "Triggered action" [:dom/prevent-default]
   "Enriched action" [:dom/prevent-default]
   "Triggered action" [:db/assoc :something/saved [:db/get :something/draft]]
   "Enriched action" [:db/assoc :something/saved "a"]
   ```
 
## It's just an example

The `action` semantics used in this example can be replaced by anything. Maybe you want to use maps, or whatever. Replicant is a library and not a framework. The library facilitates, but does not mandate, pure data oriented views. You can use regular functions in the event handlers if you like. If you want to stay in data land, by all means, feel free to be inspired by the small framework we set up in this example app.

## Pure data CSS transitions

The app also sports an example of how to use Replicant's pure data CSS transitions (the annoying banner).

## Gadget inspector

[Christian Johansen](https://github.com/cjohansen), who created Replicant, also created a data inspector that can be used for any ClojureScript app that uses an atom for its state. It is a Chrome Extension, called [gadget-inspector](https://github.com/cjohansen/gadget-inspector).

Yes, the example app uses the Gadget inspector, but to benefit from it you'll need to first build the inspector Extension and install it in your browser. There's a Babashka script included in the repo that will let you build the extension like so:

```sh
bb dev:build-inspector-extension
```

Assuming you have [Babashka](https://babashka.org/) installed.

## Dumdom

Does the code look very similar to that of a [Dumdom](https://github.com/cjohansen/dumdom) app? That's because Christian created Dumdom, and Replicant is his next iteration where he got rid of the dependency on [Snabbdom](https://github.com/snabbdom/snabbdom) and the API compatability with [Quiescent](https://github.com/levand/quiescent/), including Components. (Replicant views are just regular functions returning hiccup.)

## Happy coding! ♥️