package rxbonjour.example;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.Toast;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnItemSelected;
import butterknife.Unbinder;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.disposables.Disposables;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import rxbonjour.RxBonjour;
import rxbonjour.example.rv.Rv;
import rxbonjour.example.rv.RvBaseAdapter;
import rxbonjour.example.rv.RvBaseHolder;
import rxbonjour.model.BonjourEvent;
import rxbonjour.model.BonjourService;

/**
 * @author marcel
 */
public class DiscoveryActivity extends AppCompatActivity {

	@BindView(R.id.rv)
	Rv rvItems;
	@BindView(R.id.et_discover_type)
	EditText etDiscoverInput;

	private Unbinder unbinder;

	private RvBaseAdapter<BonjourService> adapter;

	private Disposable discovery = Disposables.empty();
	private boolean useNsdManager = false;

	@Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_discovery);
		unbinder = ButterKnife.bind(this);

		getSupportActionBar().setTitle(R.string.tv_discovery);

		// Setup RecyclerView
		rvItems.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
		adapter = new RvBaseAdapter<BonjourService>() {
			@Override protected RvBaseHolder<BonjourService> createViewHolder(LayoutInflater inflater, ViewGroup parent, int viewType) {
				return new BonjourVH(inflater, parent);
			}
		};
		rvItems.setEmptyView(ButterKnife.findById(this, R.id.tv_empty));
		rvItems.setAdapter(adapter);
	}

	@Override protected void onResume() {
		super.onResume();
		restartDiscovery();
	}

	@Override protected void onPause() {
		super.onPause();
		discovery.dispose();
	}

	@Override protected void onDestroy() {
		super.onDestroy();
		unbinder.unbind();
	}

	@OnClick(R.id.button_discover) void onDiscoverClicked() {
		CharSequence input = etDiscoverInput.getText();
		if (input != null && input.length() > 0) {
			// For non-empty input, restart the discovery with the new input
			restartDiscovery();
		}
	}

	@OnItemSelected(R.id.spinner) void onSpinnerItemSelected(AdapterView<?> adapter, View view, int position, long id) {
		// NsdManager implementation is represented by the second item in the spinner's array
		useNsdManager = (position == 1);
		restartDiscovery();
	}

	/* Begin private */

	private void restartDiscovery() {
		// Check the current input, only proceed if valid
		String input = etDiscoverInput.getText().toString();
		if (!RxBonjour.isBonjourType(input)) {
			Toast.makeText(this, getString(R.string.toast_invalidtype, input), Toast.LENGTH_SHORT).show();
			return;
		}

		// Cancel any previous subscription
		discovery.dispose();

		// Clear the adapter's items, then start a new discovery
		adapter.clearItems();
		discovery = RxBonjour.newDiscovery(this, input, useNsdManager)
				.subscribeOn(Schedulers.io())
				.observeOn(AndroidSchedulers.mainThread())
				.subscribe(new Consumer<BonjourEvent>() {
					@Override public void accept(BonjourEvent bonjourEvent) throws Exception {
						// Depending on the type of event and the availability of the item, adjust the adapter
						BonjourService item = bonjourEvent.getService();
						switch (bonjourEvent.getType()) {
							case ADDED:
								int index = indexOfAdapterItem(adapter.getItems(), item);
								if (index == -1) {
									adapter.addItem(item);
								}
								break;

							case REMOVED:
								index = indexOfAdapterItem(adapter.getItems(), item);
								if (index != -1) {
									adapter.removeItem(adapter.getItemAt(index));
								}
								break;
						}
					}
				}, new Consumer<Throwable>() {
					@Override public void accept(Throwable throwable) throws Exception {
						Toast.makeText(DiscoveryActivity.this, throwable.getMessage(), Toast.LENGTH_SHORT).show();
					}
				});
	}

	private int indexOfAdapterItem(List<BonjourService> haystack, BonjourService needle) {
		for (int i = 0, len = haystack.size(); i < len; i++) {
			BonjourService item = haystack.get(i);
			if (item.getName().equals(needle.getName()) && item.getType().equals(needle.getType())) {
				return i;
			}
		}

		return -1;
	}
}
