package rxbonjour.discovery;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.nsd.NsdManager;

import org.junit.Test;

import rxbonjour.TestBonjourListener;
import rxbonjour.base.BaseTest;
import rxbonjour.exc.TypeMalformedException;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressLint("NewApi")
public class JBBonjourDiscoveryTest extends BaseTest {

	private NsdManager nsdManager;
	private TestBonjourListener listener;

	@Override protected void setupMocks() throws Exception {
		this.listener = new TestBonjourListener();

		nsdManager = mock(NsdManager.class);

		// Wire default return values
		when(context.getSystemService(Context.NSD_SERVICE)).thenReturn(nsdManager);
	}

	@Test public void testAddAndRemoveOneCycle() throws Exception {
		BonjourDiscovery discovery = new JBBonjourDiscovery();

		discovery.start(context, "_http._tcp", listener);

		listener.assertNoErrors();
		verify(nsdManager, times(1)).discoverServices(eq("_http._tcp"), anyInt(), any(NsdManager.DiscoveryListener.class));
		discovery.stop();
		verify(nsdManager, times(1)).stopServiceDiscovery(any(NsdManager.DiscoveryListener.class));
	}

	@Test public void testAddAndRemoveOneCycleWithLocalDomain() throws Exception {
		BonjourDiscovery discovery = new JBBonjourDiscovery();

		discovery.start(context, "_http._tcp.local.", listener);

		listener.assertNoErrors();
		verify(nsdManager, times(1)).discoverServices(eq("_http._tcp.local."), anyInt(), any(NsdManager.DiscoveryListener.class));
		discovery.stop();
		verify(nsdManager, times(1)).stopServiceDiscovery(any(NsdManager.DiscoveryListener.class));
	}

	@Test public void testAddAndRemoveTwoCycle() throws Exception {
		BonjourDiscovery discovery = new JBBonjourDiscovery();
		TestBonjourListener listener1 = new TestBonjourListener();
		TestBonjourListener listener2 = new TestBonjourListener();
		discovery.start(context, "_http._tcp", listener1);
		discovery.start(context, "_http._tcp", listener2);

		listener1.assertNoErrors();
		listener2.assertNoErrors();
		verify(nsdManager, times(2)).discoverServices(eq("_http._tcp"), anyInt(), any(NsdManager.DiscoveryListener.class));
		discovery.stop();
		verify(nsdManager, times(1)).stopServiceDiscovery(any(NsdManager.DiscoveryListener.class));
		discovery.stop();
		verify(nsdManager, times(2)).stopServiceDiscovery(any(NsdManager.DiscoveryListener.class));
	}

	@Test public void testAddAndRemoveTwoDifferentTypesCycle() throws Exception {
		BonjourDiscovery discovery = new JBBonjourDiscovery();
		TestBonjourListener listener1 = new TestBonjourListener();
		TestBonjourListener listener2 = new TestBonjourListener();
		discovery.start(context, "_http._tcp", listener1);
		discovery.start(context, "_ssh._tcp", listener2);

		listener1.assertNoErrors();
		listener2.assertNoErrors();
		verify(nsdManager, times(1)).discoverServices(eq("_http._tcp"), anyInt(), any(NsdManager.DiscoveryListener.class));
		verify(nsdManager, times(1)).discoverServices(eq("_ssh._tcp"), anyInt(), any(NsdManager.DiscoveryListener.class));
		discovery.stop();
		verify(nsdManager, times(1)).stopServiceDiscovery(any(NsdManager.DiscoveryListener.class));
		discovery.stop();
		verify(nsdManager, times(2)).stopServiceDiscovery(any(NsdManager.DiscoveryListener.class));
	}

	@Test public void testRaisesExceptionOnMalformedType() throws Exception {
		BonjourDiscovery discovery = new JBBonjourDiscovery();
		discovery.start(context, "not_a-type", listener);

		listener.assertError(TypeMalformedException.class);
	}

	// TODO Fill with more tests
}
