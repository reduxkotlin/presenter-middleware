package org.reduxkotlin

import com.willowtreeapps.common.external.SelectorSubscriberBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

data class AttachView<S: Any>(val view: View<S>)
data class DetachView<S: Any>(val view: View<S>)
data class ClearView<S: Any>(val view: View<S>)

/*
 * All views implement this interface.  The PresenterFactory handles setting and removing references
 * to the dispatch() and a selectorBuilder.
 */
interface View<S : Any> {
    var dispatch: Dispatcher
    var selectorBuilder: SelectorSubscriberBuilder<S>?
}


interface PresenterProvider<S : Any> {
    fun presenter(): Presenter<View<S>> = throw NotImplementedError("Must implement this method to provide a presenterBuilder for ${this::class}")
}

interface ViewWithProvider<S : Any> : View<S>, PresenterProvider<S>

enum class ViewLifecycle {
    ATTACHED,
    DETACHED
}

data class StoreSubscriberHolder(val lifecycleState: ViewLifecycle, val subscriber: StoreSubscriber)

/**
 * PresenterMiddleware that attaches presenters with views and calls subscribers (Presenters)
 * to update the view when state changes.
 * Each view must attach/detach itself as it becomes visible/not visible by dispatching AttachView or DetachView
 * Attaching sets the presenter to the view.
 * PresenterFactory subscribes to changes in state, and passes state to presenters.
 */
fun <S : Any, V : ViewWithProvider<S>> presenterMiddleware(uiContext: CoroutineContext): Middleware = { store ->


    val uiScope = CoroutineScope(uiContext)
    val subscribers = mutableMapOf<V, StoreSubscriberHolder>()
    var subscription: StoreSubscription? = null
    val coroutineScope = CoroutineScope(uiContext)

    fun hasAttachedViews() = subscribers.isNotEmpty()

    fun onStateChange() {
        coroutineScope.launch {
            subscribers.forEach {
                if (it.value.lifecycleState == ViewLifecycle.ATTACHED) {
                    it.value.subscriber()
                }
            }
        }
    }

    fun attachView(view: V) {
//        Logger.d("AttachView: $view", Logger.Category.LIFECYCLE)
        view.dispatch = store.dispatch
        //TODO is hanging onto subscription needed?
        if (subscription == null) {
            subscription = store.subscribe(::onStateChange)
        }
        if (subscribers.containsKey(view) && subscribers[view]!!.lifecycleState == ViewLifecycle.DETACHED) {
            //view is reattached and does not need updating unless state has changed
            val subscriber = subscribers[view]!!.subscriber
            subscribers[view] = StoreSubscriberHolder(ViewLifecycle.ATTACHED, subscriber)
            subscriber()
        } else {
            val subscriber = view.presenter()(view, uiScope)(store)
            //call subscriber to trigger initial view update
            subscriber()
            subscribers[view] = StoreSubscriberHolder(ViewLifecycle.ATTACHED, subscriber)
        }
    }

    fun detachView(view: V) {
//        Logger.d("DetachView: $view", Logger.Category.LIFECYCLE)
        subscribers[view] = StoreSubscriberHolder(ViewLifecycle.DETACHED, subscribers[view]!!.subscriber)
    }

    fun clearView(view: ViewWithProvider<*>) {
//        Logger.d("ClearView: $view", Logger.Category.LIFECYCLE)
        subscribers.remove(view)

        if (!hasAttachedViews()) {
            subscription?.invoke()
            subscription = null
        }
    }

    { next: Dispatcher ->
        { action: Any ->
            when (action) {
                is AttachView<*> -> attachView(action.view as V)

                is DetachView<*> -> detachView(action.view as V)

                is ClearView<*> -> clearView(action.view as V)

                else -> next(action)
            }
        }
    }
}


/**
 * @param View a view interface that will be passed to the presenter
 * @param CoroutineScope scope on which the reselect action will be executed.  Typically a UI scope.
 */
typealias Presenter<View> = (View, CoroutineScope) -> (Store) -> StoreSubscriber

typealias PresenterBuilder<State, View> = ((View.() -> ((SelectorSubscriberBuilder<State>.() -> Unit))))

typealias PresenterBuilderWithViewArg<State, View> = ((View) -> (((SelectorSubscriberBuilder<State>.() -> Unit))))

/**
 * A convenience function for create a typed presenter builder for your App.
 *
 * usage:
 *        fun <V : LibraryView> presenter(actions: PresenterBuilder<AppState, V>): Presenter<View<AppState>> {
 *             return createGenericPresenter(actions) as Presenter<View<AppState>>
 *        }
 *
 *        val myPresenter = presenter<MyView> {{
 *            select { state.title } then { updateTitle(state.title) }
 *        }}
 *
 * @param actions - a PresenterBuilder describing actions to be taken on state changes.
 * @return a Presenter function
 *
 */
fun <State : Any, V: View<State>> createGenericPresenter(actions: PresenterBuilder<State, V>): Presenter<V> {
    return { view: V, coroutineScope ->
        { store: Store ->
            val actions2 = actions(view)
            val sel = selectorSubscriberFn(store, view, actions2)
            sel
        }
    }
}

/**
 * Helper function that creates a DSL for subscribing to changes in specific state fields and actions to take.
 * Inside the lambda there is access to the current state through the var `state`
 *
 * ex:
 *      val sel = selectorSubscriberFn {
 *          withSingleField({it.foo}, { actionWhenFooChanges() }
 *
 *          withAnyChange {
 *              //called whenever any change happens to state
 *              view.setMessage(state.barMsg) //state is current state
 *          }
 *      }
 */
fun <State : Any, V: View<State>> selectorSubscriberFn(store: Store, view: V, selectorSubscriberBuilderInit: SelectorSubscriberBuilder<State>.() -> Unit): StoreSubscriber {
    view.selectorBuilder = SelectorSubscriberBuilder(store)
    view.selectorBuilder!!.selectorSubscriberBuilderInit()
    return {
        view.selectorBuilder!!.selectorList.forEach { entry ->
            entry.key.onChangeIn(store.getState() as State) { entry.value(store.getState()) }
        }
        view.selectorBuilder!!.withAnyChangeFun?.invoke()
    }
}
