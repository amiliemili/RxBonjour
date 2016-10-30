package rxbonjour.broadcast;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;

import java.net.InetAddress;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import javax.jmdns.impl.DNSStatefulObject;

import rxbonjour.TestBonjourListener;
import rxbonjour.base.BaseTest;
import rxbonjour.exc.TypeMalformedException;

import static junit.framework.Assert.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

@PrepareForTest({ JmDNS.class, SupportBonjourBroadcast.class })
public class SupportBonjourBroadcastTest extends BaseTest {

    private TestJmDNS jmdns;
    private TestBonjourListener listener;

    abstract class TestJmDNS extends JmDNS implements DNSStatefulObject {
    }

    @Override protected void setupMocks() throws Exception {
        this.listener = new TestBonjourListener();

        WifiManager wifiManager = mock(WifiManager.class);
        WifiInfo wifiInfo = mock(WifiInfo.class);
        WifiManager.MulticastLock lock = mock(WifiManager.MulticastLock.class);
        jmdns = mock(TestJmDNS.class);

        // Wire default return values
        when(context.getSystemService(Context.WIFI_SERVICE)).thenReturn(wifiManager);
        when(wifiManager.createMulticastLock(anyString())).thenReturn(lock);
        when(wifiInfo.getIpAddress()).thenReturn(0);
        when(wifiManager.getConnectionInfo()).thenReturn(wifiInfo);

        // Mock statics
        mockStatic(JmDNS.class);

        // Wire statics
        given(JmDNS.create(any(InetAddress.class), anyString())).willReturn(jmdns);
    }

    private void setJmDNSMockClosed() {
        when(jmdns.isClosing()).thenReturn(true);
        when(jmdns.isClosed()).thenReturn(true);
    }

    @Test public void testAddAndRemoveOneCycle() throws Exception {
        BonjourBroadcastBuilder builder = PowerMockito.spy(SupportBonjourBroadcast.newBuilder("_http._tcp"));
        BonjourBroadcast<?> broadcast = builder.build();
        ArgumentCaptor<ServiceInfo> captor = ArgumentCaptor.forClass(ServiceInfo.class);

        broadcast.start(context, listener);
        listener.assertNoErrors();

        verify(jmdns, times(1)).registerService(captor.capture());
        ServiceInfo serviceInfo = captor.getValue();
        assertEquals(serviceInfo.getType(), "_http._tcp.local.");

        broadcast.stop();
        verify(jmdns, times(1)).unregisterService(serviceInfo);
        verify(jmdns, times(1)).close();
        setJmDNSMockClosed();
    }

    @Test public void testAddAndRemoveOneCycleWithLocalDomain() throws Exception {
        BonjourBroadcastBuilder builder = PowerMockito.spy(new SupportBonjourBroadcast.Builder("_http._tcp.local."));
        BonjourBroadcast<?> broadcast = builder.build();
        ArgumentCaptor<ServiceInfo> captor = ArgumentCaptor.forClass(ServiceInfo.class);

        broadcast.start(context, listener);
        listener.assertNoErrors();

        verify(jmdns, times(1)).registerService(captor.capture());
        ServiceInfo serviceInfo = captor.getValue();
        assertEquals(serviceInfo.getType(), "_http._tcp.local.");

        broadcast.stop();
        verify(jmdns, times(1)).unregisterService(serviceInfo);
        verify(jmdns, times(1)).close();
        setJmDNSMockClosed();
    }

    @Test public void testAddAndRemoveTwoDifferentBroadcast() throws Exception {
        BonjourBroadcastBuilder bd1 = PowerMockito.spy(new SupportBonjourBroadcast.Builder("_http._tcp"));
        BonjourBroadcast<?> bc1 = bd1.build();

        BonjourBroadcastBuilder bd2 = PowerMockito.spy(new SupportBonjourBroadcast.Builder("_ftp._tcp"));
        BonjourBroadcast<?> bc2 = bd2.build();

        TestBonjourListener listener1 = new TestBonjourListener();
        TestBonjourListener listener2 = new TestBonjourListener();
        ArgumentCaptor<ServiceInfo> captor = ArgumentCaptor.forClass(ServiceInfo.class);

        bc1.start(context, listener1);
        listener1.assertNoErrors();

        verify(jmdns, times(1)).registerService(captor.capture());
        ServiceInfo si1 = captor.getValue();
        assertEquals(si1.getType(), "_http._tcp.local.");

        bc2.start(context, listener2);
        listener2.assertNoErrors();

        verify(jmdns, times(2)).registerService(captor.capture());
        ServiceInfo si2 = captor.getValue();
        assertEquals(si2.getType(), "_ftp._tcp.local.");

        bc1.stop();
        verify(jmdns, times(1)).unregisterService(si1);
        verify(jmdns, never()).close();

        bc2.stop();
        verify(jmdns, times(1)).unregisterService(si2);
        verify(jmdns, times(1)).close();
        setJmDNSMockClosed();
    }

    @Test public void testRaisesExceptionOnMalformedType() throws Exception {
        BonjourBroadcastBuilder builder = PowerMockito.spy(SupportBonjourBroadcast.newBuilder("not_a-type"));
        BonjourBroadcast<?> broadcast = builder.build();

        broadcast.start(context, listener);

        listener.assertError(TypeMalformedException.class);
    }
}
