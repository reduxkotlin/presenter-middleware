# Presenter Middleware


![badge][badge-android]
![badge][badge-native]
![badge][badge-js]
![badge][badge-jvm]
![badge][badge-linux]
![badge][badge-windows]
![badge][badge-mac]
![badge][badge-wasm]


Write concise multiplatform presenter functions with presenter-middleware:

```
interface StartView : GameBaseView {
    fun showLoading()
    fun hideLoading()
    fun showError(msg: String)

    override fun presenter(): Presenter<View, AppState> = startPresenter
}

val startPresenter = presenter<StartView> {
    {
        select { state.isLoadingItems } then {
            if (state.isLoadingItems) {
                showLoading()
            } else {
                hideLoading()
            }
        }

        select { state.errorLoadingItems } then { showError(state.errorMsg) }
    }
}

```
Presenter-middleware handles wiring this up to your view and store subscriptions.  Just implement the interface on Android and iOS and dispatch lifecycle events.  See the [ReadingList sample app](https://github.com/reduxkotlin/ReadingListSampleApp) or the [Name Game Sample App](https://github.com/reduxkotlin/NameGameSampleApp) for example usage.



__How to add to project:__

Artifacts are hosted on maven central.  For multiplatform, add the following to your shared module:

```
kotlin {
  sourceSets {
        commonMain { //   <---  name may vary on your project
            dependencies {
                implementation "org.reduxkotlin:presenter-middlware:0.2.8"
            }
        }
 }
```

Add the presetner store-enhancer:
```
  val appStore = createStore(reducer, AppState.INITIAL_STATE,
                    compose(listOf(presenterEnhancer(uiContext),
                            applyMiddleware(createThunkMiddleware())
```

For JVM only:
```
  implementation "org.reduxkotlin:presenter-middleware-jvm:0.2.8"
```

[badge-android]: http://img.shields.io/badge/platform-android-brightgreen.svg?style=flat
[badge-native]: http://img.shields.io/badge/platform-native-lightgrey.svg?style=flat	
[badge-native]: http://img.shields.io/badge/platform-native-lightgrey.svg?style=flat
[badge-js]: http://img.shields.io/badge/platform-js-yellow.svg?style=flat
[badge-js]: http://img.shields.io/badge/platform-js-yellow.svg?style=flat
[badge-jvm]: http://img.shields.io/badge/platform-jvm-orange.svg?style=flat
[badge-jvm]: http://img.shields.io/badge/platform-jvm-orange.svg?style=flat
[badge-linux]: http://img.shields.io/badge/platform-linux-important.svg?style=flat
[badge-linux]: http://img.shields.io/badge/platform-linux-important.svg?style=flat 
[badge-windows]: http://img.shields.io/badge/platform-windows-informational.svg?style=flat
[badge-windows]: http://img.shields.io/badge/platform-windows-informational.svg?style=flat
[badge-mac]: http://img.shields.io/badge/platform-macos-lightgrey.svg?style=flat
[badge-mac]: http://img.shields.io/badge/platform-macos-lightgrey.svg?style=flat
[badge-wasm]: https://img.shields.io/badge/platform-wasm-darkblue.svg?style=flat
