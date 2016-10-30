package rxbonjour.model;

import android.support.annotation.NonNull;

public interface BonjourListener {

	void onBonjourEvent(@NonNull BonjourEvent event);
	void onBonjourError(@NonNull Throwable t);
}
