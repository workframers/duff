<img src="https://raw.githubusercontent.com/workframers/duff/master/resources/duff-logo.png" width="442px">

[![Clojars Project](https://img.shields.io/clojars/v/com.workframe/duff.svg)](https://clojars.org/com.workframe/duff)

Form state management for re-frame

### API Overview

#### `duff.core/create-form`

This is the main higher order component you wrap your main form in. Internally sets up a React provider for `duff.core/field` components to connect to.

Takes a configuration map and a component, this configuration can be:

```clojure
{:form-name "Required: The name of the form, should be a keyword, used as the form state namespace internally"
 :initial-value "Optional: Initial value is a function that takes props and returns a map of initial values for the form"
 :validate "Optional: A validation function for the form. When any value on the form changes, this function will be called. How to set this up is documented below"
 :persist? "Optional: If you want the form state to persist when the form is unmounted from the DOM defaults to false"}
```

The 'dumb component' gets a lot of various props related to the form, you'll mainly just use the `on-submit` function provided by Duff

Full list here:

```clojure
{:handle-submit "Function used to submit the form"
 :values        "The entire form values, not normally necessary at the root component, as individual form fields are connected to their values"
 :pristine?     "The current form value is the initial form value"
 :errors        "The entire form errors map, not normally necessary at the root component, as individual form fields are connected to their errors"
 :dirty?        "Opposite of pristine?"
 :reset         "Function to reset the form to the initial value"
 :submitted?    "Form is in a submitted state"
 :valid?        "The form has no errors"
 :invalid?      "The form has errors"
 :disabled?     "True if the form is submitted or invalid. Useful for disabling the submit button."}
```

Ex:

```clojure
(defn my-input [{:keys [value on-change]}]
  [:input {:value value :on-change on-change}])
  
(defn my-form* [{:keys [handle-submit]}]
  [:form {:on-submit handle-submit}
   [duff.core/field {:name :email
                     :component my-input}]
   [:button {:type "submit"} "Submit"]])
   
(def my-form
  (duff.core/create-form
   {:form-name :my-form
    :initial-value (fn [props]
                     {:email (:email props)})
    :validate (fn [form-val]
                (when (empty? (:email form-val))
                  {:email "Email can't be blank."}))
    ;; I think normally the default of false works for most cases
    :persist? true}))
    
(defn my-form-app []
  (let [email (get-user-email-somehow)]
    [:div
     [my-form {:email email
               :on-submit (fn [form-val]
                            ;;The on-submit value you pass here is called by Duff when on submit is triggered
                            ;;And validation passes
                            (actions/login (:email form-val)))}]]))
```

#### `duff.core/field`

This is the higher order component that wraps individual form fields somewhere below the root create-form component in the view tree.

Connects form field components to the store, provides `on-change` and `value` (and more) properties that your component can use to update the store

Takes a configuration map, which could be:

```clojure
{:name "Required: The name of the field, should be a keyword, the value of the field will be stored at this key"
 :component "Required: The dumb component, to work with Duff the component needs to handle `on-change` and `value` properties"
 :format "Optional: A function that is called with the store value before being passed to the component. Useful when the field value is a Date or something"
 :parse "Optional: A function that is called with the value from `on-change` before being saved into the store"
 :getter "Optional: Defaults to `(fn [e] (.-value (.-target e)))` because it assumes you're using native form components which call on-change directly, can be overridden"}
```

The 'dumb component' gets a lot of various props related to the field, you'll mainly just use the `on-change` and `value` props provided by Duff

Full list here:

```clojure
{:value      "The field value"
 :on-change  "Function you call with a value for this field"
 :value?     "Does the field have a value?"
 :pristine?  "The field value is equal to the initial value"
 :dirty?     "The field value is not equal to the initial value"
 :errors?    "The field has errors and it's dirty"
 :errors     "Errors for this specific field"
 :field-name "The name of the field"}
```

An example wrapping a native input is in the example above, but for a 'cusome field', could look something like:

```clojure
(defn custom-field [{:keys [value on-change errors errors?]}]
  [:div {:style (merge {:color "black"}
                       (when errors?
                         {:border "1px solid red"}))}
   (when errors?
     (for [error errors]
       ^{:key error}
       [:div {:class "form-error"} error]))
   [:input {:on-change on-change :value value}]])

;; In some other component, render the custom-field, connecting it to the form store
[duff.core/field {:name :user-email :component custom-field}]
```

#### `duff.core/connect-to-fields`

Higher order component that wraps a dumb component, adding other field information to the props of the dumb component. Useful if a form field has custom logic that changes depending on other form values.

Takes a vector of form field names (the keywords passed to the field's `:name`) and a component.

For each field you request, a property of the same name will have this information:

```clojure
{:initial-value "The field's initial value"
 :value         "The current field value"
 :errors        "The field's errors"
 :pristine?     "The field's value equals the initial value"
 :on-change     "The field's on-change function"
 :dirty?        "The field is not pristine"}
```

Higher order components can be chained, as they act like middleware, decorating a component's props and passing them through.

An example building off of the above's `custom-field`

```clojure
(def email-field
  (->> custom-field
       (some-other-higher-order-component {:is? true})
       (duff.core/connect-to-fields [:password :birthday])))
```

Inside `custom-field` `(:password props)` will yield a map with the above signature. Same is true with `(:birthday props)`

#### `duff.core/make-validation`

Helper to make a validation function for the form. At it's core, a validation function returns `nil` if the form has no errors, and if it has errors, returns a map where the keywords are a field name, and the value is a error string, or a vector of error strings.

This helper adds some sugar to that process.

Ex.

```clojure
(def validation (duff.core/make-validation {:title [{:pred empty?
                                                      :text "Enter title to save changes"}
                                                     {:pred (fn [val] (> 5 (count val)))
                                                      :text "must be more than five characters"}]
                                            :email (fn [val] (when (empty? val) "Email can't be empty"))}))
```

The value of each key (the field's name) can be one of:

- A function that returns `nil` if valid, or an error string
- A vector of functions with the above siganture
- A map with a `:pred` function, and if it returns a non `nil?` value, the `:text` value will be used as the error string
- A vector of maps with the above signature

#### `duff.core/make-synthetic-event`

Helper that wraps a Clojure value in a native form event JS object. Makes a custom component compatible with the field's default `getter` function

```clojure
(let [name (str (take 100 (repeat "AAAA")))]
  ;; The on change handler given to the field by Duff
  (on-change
    (duff.core/make-synthetic-event name)))
```

#### `duff.core/query`

Helper function you can use to ask questions about the current form state

Takes the form name keyword, and the query key, which could be:

- `:pristine?`
- `:errors`
- `:dirty?`
- `:value`
- `:valid?`
- `:invalid`
- `:submitted?`
- `:should-confirm?`

Usage:

```clojure
(duff.core/query :my-form :dirty?)
```

#### `duff.core/component-partial`

`component-partial` takes a component and map of props, and returns a component}
that when rendered, will merge the partially applied map of props to the props
passed in at runtime. This is useful if you need to partially apply props to a
component before passing it a helper which will render it later with additional
props (e.g. form utils).

We use memoize so that you can partially apply components
in render methods. If we didn't memoize, a new version would be created every
re-render. This is particularly bad if the component you're using it on is a
form input. because every value change will cause the input to lose focus.

Ex:

```clojure
(defn my-form-component [{:keys [registered?]}]
  [:div
   [duff.core/field {:name :email
                     :component (duff.core/component-partial some-field-component
                                                             {:registered? registered?
                                                              :style {:margin-top 5}})}]])
```

## License

Copyright © 2018 Workframe, Inc.

Distributed under the Apache License, Version 2.0.
