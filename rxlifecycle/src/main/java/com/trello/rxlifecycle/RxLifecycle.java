/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.trello.rxlifecycle;

import rx.Observable;
import rx.functions.Func1;
import rx.functions.Func2;

public class RxLifecycle {

    private RxLifecycle() {
        throw new AssertionError("No instances");
    }

    /**
     * Binds the given source to a Fragment lifecycle.
     * <p>
     * When the lifecycle event occurs, the source will cease to emit any notifications.
     * <p>
     * Use with {@link Observable#compose(Observable.Transformer)}:
     * {@code source.compose(RxLifecycle.bindUntilEvent(lifecycle, FragmentEvent.STOP)).subscribe()}
     * <p>
     * Warning: In order for this to work in all possible cases, this should only be called
     * immediately before calling subscribe().
     *
     * @param lifecycle the Fragment lifecycle sequence
     * @param event the event which should conclude notifications from the source
     * @return a reusable {@link Observable.Transformer} that unsubscribes the source at the specified event
     */
    public static <T> Observable.Transformer<T, T> bindUntilFragmentEvent(
        final Observable<FragmentEvent> lifecycle, final FragmentEvent event) {
        return bindUntilEvent(lifecycle, event);
    }

    /**
     * Binds the given source to an Activity lifecycle.
     * <p>
     * When the lifecycle event occurs, the source will cease to emit any notifications.
     * <p>
     * Use with {@link Observable#compose(Observable.Transformer)}:
     * {@code source.compose(RxLifecycle.bindUntilEvent(lifecycle, ActivityEvent.STOP)).subscribe()}
     * <p>
     * Warning: In order for this to work in all possible cases, this should only be called
     * immediately before calling subscribe().
     *
     * @param lifecycle the Activity lifecycle sequence
     * @param event the event which should conclude notifications from the source
     * @return a reusable {@link Observable.Transformer} that unsubscribes the source at the specified event
     */
    public static <T> Observable.Transformer<T, T> bindUntilActivityEvent(
        final Observable<ActivityEvent> lifecycle, final ActivityEvent event) {
        return bindUntilEvent(lifecycle, event);
    }

    private static <T, R> Observable.Transformer<T, T> bindUntilEvent(final Observable<R> lifecycle,
                                                                      final R event) {
        if (lifecycle == null) {
            throw new IllegalArgumentException("Lifecycle must be given");
        }

        return new Observable.Transformer<T, T>() {
            @Override
            public Observable<T> call(Observable<T> source) {
                return source.takeUntil(
                    lifecycle.takeFirst(new Func1<R, Boolean>() {
                        @Override
                        public Boolean call(R lifecycleEvent) {
                            return lifecycleEvent == event;
                        }
                    })
                );
            }
        };
    }

    /**
     * Binds the given source to an Activity lifecycle.
     * <p>
     * Use with {@link Observable#compose(Observable.Transformer)}:
     * {@code source.compose(RxLifecycle.bindActivity(lifecycle)).subscribe()}
     * <p>
     * This helper automatically determines (based on the lifecycle sequence itself) when the source
     * should stop emitting items. In the case that the lifecycle sequence is in the
     * creation phase (CREATE, START, etc) it will choose the equivalent destructive phase (DESTROY,
     * STOP, etc). If used in the destructive phase, the notifications will cease at the next event;
     * for example, if used in PAUSE, it will unsubscribe in STOP.
     * <p>
     * Due to the differences between the Activity and Fragment lifecycles, this method should only
     * be used for an Activity lifecycle.
     * <p>
     * Warning: In order for this to work in all possible cases, this should only be called
     * immediately before calling subscribe().
     *
     * @param lifecycle the lifecycle sequence of an Activity
     * * @return a reusable {@link Observable.Transformer} that unsubscribes the source during the Activity lifecycle
     */
    public static <T> Observable.Transformer<T, T> bindActivity(Observable<ActivityEvent> lifecycle) {
        return bind(lifecycle, ACTIVITY_LIFECYCLE);
    }

    /**
     * Binds the given source to a Fragment lifecycle.
     * <p>
     * Use with {@link Observable#compose(Observable.Transformer)}:
     * {@code source.compose(RxLifecycle.bindFragment(lifecycle)).subscribe()}
     * <p>
     * This helper automatically determines (based on the lifecycle sequence itself) when the source
     * should stop emitting items. In the case that the lifecycle sequence is in the
     * creation phase (CREATE, START, etc) it will choose the equivalent destructive phase (DESTROY,
     * STOP, etc). If used in the destructive phase, the notifications will cease at the next event;
     * for example, if used in PAUSE, it will unsubscribe in STOP.
     * <p>
     * Due to the differences between the Activity and Fragment lifecycles, this method should only
     * be used for a Fragment lifecycle.
     * <p>
     * Warning: In order for this to work in all possible cases, this should only be called
     * immediately before calling subscribe().
     *
     * @param lifecycle the lifecycle sequence of a Fragment
     * @return a reusable {@link Observable.Transformer} that unsubscribes the source during the Fragment lifecycle
     */
    public static <T> Observable.Transformer<T, T> bindFragment(Observable<FragmentEvent> lifecycle) {
        return bind(lifecycle, FRAGMENT_LIFECYCLE);
    }

    private static <T, R> Observable.Transformer<T, T> bind(Observable<R> lifecycle,
                                                            final Func1<R, R> correspondingEvents) {
        if (lifecycle == null) {
            throw new IllegalArgumentException("Lifecycle must be given");
        }

        // Make sure we're truly comparing a single stream to itself
        final Observable<R> sharedLifecycle = lifecycle.share();

        // Keep emitting from source until the corresponding event occurs in the lifecycle
        return new Observable.Transformer<T, T>() {
            @Override
            public Observable<T> call(Observable<T> source) {
                return source.takeUntil(
                    Observable.combineLatest(
                        sharedLifecycle.take(1).map(correspondingEvents),
                        sharedLifecycle.skip(1),
                        new Func2<R, R, Boolean>() {
                            @Override
                            public Boolean call(R bindUntilEvent, R lifecycleEvent) {
                                return lifecycleEvent == bindUntilEvent;
                            }
                        })
                        .takeFirst(new Func1<Boolean, Boolean>() {
                            @Override
                            public Boolean call(Boolean shouldComplete) {
                                return shouldComplete;
                            }
                        })
                );
            }
        };
    }

    // Figures out which corresponding next lifecycle event in which to unsubscribe, for Activities
    private static final Func1<ActivityEvent, ActivityEvent> ACTIVITY_LIFECYCLE =
        new Func1<ActivityEvent, ActivityEvent>() {
            @Override
            public ActivityEvent call(ActivityEvent lastEvent) {
                if (lastEvent == null) {
                    throw new NullPointerException("Cannot bind to null ActivityEvent.");
                }

                switch (lastEvent) {
                    case CREATE:
                        return ActivityEvent.DESTROY;
                    case START:
                        return ActivityEvent.STOP;
                    case RESUME:
                        return ActivityEvent.PAUSE;
                    case PAUSE:
                        return ActivityEvent.STOP;
                    case STOP:
                        return ActivityEvent.DESTROY;
                    case DESTROY:
                        throw new IllegalStateException("Cannot bind to Activity lifecycle when outside of it.");
                    default:
                        throw new UnsupportedOperationException("Binding to " + lastEvent + " not yet implemented");
                }
            }
        };

    // Figures out which corresponding next lifecycle event in which to unsubscribe, for Fragments
    private static final Func1<FragmentEvent, FragmentEvent> FRAGMENT_LIFECYCLE =
        new Func1<FragmentEvent, FragmentEvent>() {
            @Override
            public FragmentEvent call(FragmentEvent lastEvent) {
                if (lastEvent == null) {
                    throw new NullPointerException("Cannot bind to null FragmentEvent.");
                }

                switch (lastEvent) {
                    case ATTACH:
                        return FragmentEvent.DETACH;
                    case CREATE:
                        return FragmentEvent.DESTROY;
                    case CREATE_VIEW:
                        return FragmentEvent.DESTROY_VIEW;
                    case START:
                        return FragmentEvent.STOP;
                    case RESUME:
                        return FragmentEvent.PAUSE;
                    case PAUSE:
                        return FragmentEvent.STOP;
                    case STOP:
                        return FragmentEvent.DESTROY_VIEW;
                    case DESTROY_VIEW:
                        return FragmentEvent.DESTROY;
                    case DESTROY:
                        return FragmentEvent.DETACH;
                    case DETACH:
                        throw new IllegalStateException("Cannot bind to Fragment lifecycle when outside of it.");
                    default:
                        throw new UnsupportedOperationException("Binding to " + lastEvent + " not yet implemented");
                }
            }
        };
}
