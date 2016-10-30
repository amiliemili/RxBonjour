package rxbonjour.base;

import android.content.Context;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.mockito.Mockito.mock;

@RunWith(PowerMockRunner.class)
public abstract class BaseTest {

	protected final Context context;

	protected BaseTest() {
		this.context = mock(Context.class);
	}

	@Before public final void beforeTests() throws Exception {
		setupMocks();
	}

	protected void setupMocks() throws Exception {
	}
}
