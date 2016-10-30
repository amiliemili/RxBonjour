package rxbonjour.internal;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Backlog manager class, polling objects and processing them until an external object calls
 * {@link #proceed()}.
 */
public abstract class Backlog<T> {

	/** Item sent to the Backlog upon requesting to quit processing with it */
	private static final Object STOP_MARKER = new Object();

	/** Queue to which pending objects are added */
	private BlockingQueue<Object> queue = new LinkedBlockingQueue<>(32);
	private final Thread processingThread;

	/** Busy flag, set upon processing an item, until {@link #proceed()} is called */
	private AtomicBoolean idle = new AtomicBoolean(true);

	/**
	 * Constructor
	 */
	public Backlog() {
		this.processingThread = new Thread() {
			@Override public void run() {
				super.run();

				try {
					// Take an item pushed to the queue and check for the STOP marker
					Object info = queue.take();
					while (!idle.get()) {
						// ;
						sleep(100);
					}

					if (!STOP_MARKER.equals(info)) {
						// If an item other than the STOP marker is added,
						// invoke the onNext callback with it
						idle.set(false);

						//noinspection unchecked
						onNext(Backlog.this, (T) info);
					}

				} catch (InterruptedException ignored) {
				}
			}
		};
		this.processingThread.start();
	}

	/**
	 * Terminates the work of this backlog instance
	 */
	public void quit() {
		// Send the STOP signal to the queue
		queue.add(STOP_MARKER);
		processingThread.interrupt();
	}

	/**
	 * Adds the provided item to the backlog's queue for processing
	 * @param item	Item enqueued to the backlog
	 */
	public void add(T item) {
		// Add to the queue, and if ready for another item, proceed right away
		queue.add(item);
		if (idle.get()) proceed();
	}

	/**
	 * Signalizes that the backlog can proceed with the next item
	 */
	public void proceed() {
		idle.set(true);
	}

	/* Begin abstract */

	/**
	 * Callback invoked upon processing an item. This method is executed on a background thread;
	 * after the caller is done processing the item, it is his responsibility to call {@link #proceed()}
	 * or {@link #quit()} on the backlog.
	 * @param item	Item to be processed next
	 */
	public abstract void onNext(Backlog<T> backlog, T item);
}
