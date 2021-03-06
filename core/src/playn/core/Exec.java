/**
 * Copyright 2010-2015 The PlayN Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package playn.core;

import java.util.ArrayList;
import java.util.List;

import react.RPromise;
import react.Signal;
import react.Slot;

/**
 * Handles execution of units of code, both on background threads ({@link #invokeAsync}) and on the
 * main PlayN game thread ({@link #invokeLater}).
 */
public abstract class Exec {

  /** A default exec implementation which processes {@link #invokeLater} via the frame tick. */
  public static abstract class Default extends Exec {
    private final List<Runnable> pending = new ArrayList<>();
    private final List<Runnable> running = new ArrayList<>();
    protected final Platform plat;

    public Default (Platform plat) {
      this.plat = plat;
      plat.frame.connect(new Slot<Object>() {
        public void onEmit (Object unused) { dispatch(); }
      }).atPrio(Short.MAX_VALUE);
    }

    @Override public synchronized void invokeNextFrame (Runnable action) {
      pending.add(action);
    }

    @Override public synchronized void invokeLater (Runnable action) {
      pending.add(action);
    }

    private void dispatch () {
      synchronized (this) {
        running.addAll(pending);
        pending.clear();
      }

      for (int ii = 0, ll = running.size(); ii < ll; ii++) {
        Runnable action = running.get(ii);
        try {
          action.run();
        } catch (Throwable e) {
          plat.reportError("invokeLater Runnable failed: " + action, e);
        }
      }
      running.clear();
    }
  }

  /**
   * Returns true if the caller is running on the 'main' game thread, false otherwise.
   */
  public abstract boolean isMainThread ();

  /**
   * Invokes {@code action} on the next {@link Platform#frame} signal. The default implementation
   * listens to the frame signal at a very high priority so that invoke later actions will run
   * before the game's normal callbacks. <em>Note:</em> if the game is paused, these actions will
   * not run until it is unpaused and the next game frame is processed.
   */
  public abstract void invokeNextFrame (Runnable action);

  /**
   * Invokes {@code action} on the next {@link Platform#frame} signal <em>or</em>, if the game is
   * paused, on the OS UI thread. Actions posted here will still be run in order and in a single
   * threaded manner (like {@link #invokeNextFrame}), but they are not guaranteed to run on the
   * game thread if the game is paused during the frame on which actions are posted.
   *
   * <p>If you're deferring a graphics or (game) UI action, you should almost certainly use
   * {@link #invokeNextFrame}, but if you are deferring an action like saving data or initiating a
   * network connection, you may wish to use {@code invokeLater} to ensure that those actions are
   * completed even if the player happens to pause the game (by backgrounding the app on mobile,
   * for example) immediately after they are queued up.
   */
  public abstract void invokeLater (Runnable action);

  /**
   * Creates a promise which defers notification of success or failure to the game thread,
   * regardless of what thread on which it is completed. Note that even if it is completed on the
   * game thread, it will still defer completion until the next frame.
   */
  public <T> RPromise<T> deferredPromise () {
    return new RPromise<T>() {
      @Override public void succeed (final T value) {
        invokeLater(new Runnable() {
          public void run () { superSucceed(value); }
        });
      }
      @Override public void fail (final Throwable cause) {
        invokeLater(new Runnable() {
          public void run () { superFail(cause); }
        });
      }
      private void superSucceed (T value) { super.succeed(value); }
      private void superFail (Throwable cause) { super.fail(cause); }
    };
  }

  /**
   * Returns whether this platform supports async (background) operations.
   * HTML doesn't, most other platforms do.
   */
  public boolean isAsyncSupported () {
    return false;
  }

  /**
   * Invokes the supplied action on a separate thread.
   * @throws UnsupportedOperationException if the platform does not support async operations.
   */
  public void invokeAsync (Runnable action) {
    throw new UnsupportedOperationException();
  }
}
