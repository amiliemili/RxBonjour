package rxbonjour;

import android.support.annotation.NonNull;

import junit.framework.Assert;

import java.util.ArrayList;
import java.util.List;

import rxbonjour.model.BonjourEvent;
import rxbonjour.model.BonjourListener;

public final class TestBonjourListener implements BonjourListener {

	private final List<BonjourEvent> events = new ArrayList<>();
	private Throwable error;

	@Override public void onBonjourEvent(@NonNull BonjourEvent event) {
		events.add(event);
	}

	@Override public void onBonjourError(@NonNull Throwable t) {
		error = t;
	}

	/* Begin public */

	public void assertNoErrors() {
		Assert.assertEquals(null, error);
	}

	public void assertError(Class<? extends Throwable> cls) {
		Assert.assertNotNull(error);
		Assert.assertEquals(cls, error.getClass());
	}
}
