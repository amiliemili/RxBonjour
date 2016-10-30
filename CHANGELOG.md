# 1.0.0 (09/24/2016)

Most of RxBonjour's API has remained stable with the release of version 1.0.x. However, since some of the internals of the library have been revisited, usage differs slightly from the 0.x releases. Here's how:

### Removed methods

The following table presents a list of removed APIs, and their replacements going forward from 1.0.x. If your code isn't compiling anymore because of unresolved method references or reduced visibility, check out this table to find a suitable replacement:

|Removed API|Replace with|
|---|---|
|`RxBonjour.startDiscovery(Context, String)`|`RxBonjour.newDiscovery(Context, String)`|
|`RxBonjour.startDiscovery(Context, String, boolean)`|`RxBonjour.newDiscovery(Context, String, boolean)`|
|`BonjourBroadcastBuilder.build()`|`BonjourBroadcastBuilder.start(Context)`|

### Malformed Type Notifications

Before 1.0.x, `RxBonjour.newDiscovery()` and `RxBonjour.newBroadcast()` would throw a `TypeMalformedException` in case the provided service type was invalid according to Bonjour's expectations. This behaviour has been changed and moved into the created `Observable` objects themselves, which now emit this exception in `onError(Throwable)` after they are being subscribed to. Handling of arbitrary service types by your users should still pass `RxBonjour.isBonjourType(String)` before being passed through to RxBonjour - this behavior remains the same as before.

### Threading

And now for the most jarring change from previous versions: The `Observables` returned by `RxBonjour.newDiscovery()` and `BroadcastBroadcastBuilder.start()` are **no longer pre-configured to be subscribed on an I/O thread**! This will cause issues if you have relied on the library taking care of the threading before. With this pre-configuration removed, embedding RxBonjour into more complex Observable chains is facilitated.

Check your usage of `Observable` objects obtained from RxBonjour, and apply `subscribeOn()` and `observeOn()` yourself to avoid having them execute on the main thread:

```java
// Before:
Subscription subscriber = RxBonjour.newDiscovery(this, "_http._tcp")
    .subscribe(
        this::handleEvent,
        this::handleError);

// After:
Subscription subscriber = RxBonjour.newDiscovery(this, "_http._tcp")
    .subscribeOn(Schedulers.io())
    .observeOn(AndroidSchedulers.mainThread())
    .subscribe(
        this::handleEvent,
        this::handleError);
```

# 0.x.y

*here be dragons*
