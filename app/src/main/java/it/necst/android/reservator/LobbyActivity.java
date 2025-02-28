package it.necst.android.reservator;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.widget.LinearLayout;
import android.widget.TextView;

import it.necst.android.reservator.common.PreferenceManager;
import it.necst.android.reservator.model.AddressBook;
import it.necst.android.reservator.model.AddressBookUpdatedListener;
import it.necst.android.reservator.model.DataProxy;
import it.necst.android.reservator.model.DataUpdatedListener;
import it.necst.android.reservator.model.ReservatorException;
import it.necst.android.reservator.model.Room;
import it.necst.android.reservator.view.LobbyReservationRowView;
import it.necst.android.reservator.view.LobbyReservationRowView
        .OnReserveListener;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;

import butterknife.BindView;
import butterknife.ButterKnife;

public class LobbyActivity extends ReservatorActivity
        implements OnMenuItemClickListener,
        DataUpdatedListener, AddressBookUpdatedListener {
    MenuItem settingsMenu, refreshMenu, aboutMenu;
    DataProxy proxy;
    AddressBook ab;
    int showLoadingCount = 0;

    AlertDialog alertDialog;
    private ProgressDialog progressDialog = null;
    private boolean waitingAddresses = false;

    @BindView(R.id.digitalClock1)
    TextView clock;
    @BindView(R.id.linearLayout1)
    LinearLayout container;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.lobby_activity);
        ButterKnife.bind(this);
        ab = getResApplication().getAddressBook();
        clock.setTypeface(
                Typeface.createFromAsset(getAssets(), "fonts/EHSMB.TTF"));
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getAvailableAccounts().length <= 0) {
            showSetupWizard();
        }
        showLoadingCount = 0; //TODO better fix
        proxy = this.getResApplication().getDataProxy();
        proxy.addDataUpdatedListener(this);
        if (ab == null) {
            ab = getResApplication().getAddressBook();
        }
        if (ab != null) {
            ab.addDataUpdatedListener(this);
        }
        refreshRoomInfo();
    }

    public String[] getAvailableAccounts() {
        List<String> accountsList = new ArrayList<>();
        for (Account account : AccountManager
                .get(this)
                .getAccountsByType(getString(R.string.googleAccountType))) {
            accountsList.add(account.name);
        }
        return accountsList.toArray(new String[accountsList.size()]);
    }

    private void showSetupWizard() {
        final Intent i = new Intent(this, WizardActivity.class);
        startActivity(i);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (proxy != null) {
            proxy = null;
        }

        ab.addDataUpdatedListener(this);
        if (progressDialog != null) {
            progressDialog.dismiss();
            showLoadingCount = 0;
            progressDialog = null;
        }
    }

    @Override
    protected Boolean isPrehensible() {
        String favouriteRoomName =
                PreferenceManager.getInstance(this).getSelectedRoom();
        return favouriteRoomName != null;
    }

    private void refreshRoomInfo() {
        updateLoadingWindow(1);
        container.removeAllViews();
        proxy.refreshRooms();
    }

    /*
     * @param howMuch If howMuch > 0, an item has been added to our
     * ProgressDialog and
     * we need to increase the number of max items. If howMuch is less than
     * zero,
     * something has been completed and we can increase the progress. This
     * should be
     * split to two different functions, incrementMaxItems() and itemFinished(),
     * because (at least in the current version) there is always only one
     * increment
     * at time.
     */
    private void updateLoadingWindow(int howMuch) {
        showLoadingCount += howMuch;

        if (showLoadingCount <= 0 && progressDialog != null) {
            progressDialog.dismiss();
            progressDialog = null;
            return;
        }

        if (showLoadingCount > 0 && (progressDialog == null || !progressDialog
                .isShowing())) {
            if (progressDialog != null) {
                progressDialog.dismiss();
            }
            progressDialog = constructNewProgressDialog();
            progressDialog.setMax(showLoadingCount);
            progressDialog.show();
        }

        if (progressDialog != null) {
            // Increment the maximum number of items if needed
            if (showLoadingCount > progressDialog.getMax()) {
                progressDialog.setMax(showLoadingCount);
            }
            // "howMuch < 0" => something has been completed.
            if (howMuch < 0) {
                progressDialog.incrementProgressBy(Math.abs(howMuch));
            }
        }
    }

    private ProgressDialog constructNewProgressDialog() {
        ProgressDialog d = new ProgressDialog(this);
        d.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        d.setMessage("Refreshing room list...");
        d.setCancelable(true);
        d.setMax(1);
        return d;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        refreshMenu = menu.add("Refresh").setOnMenuItemClickListener(this);
        refreshMenu.setIcon(android.R.drawable.ic_popup_sync);
        settingsMenu = menu.add("Settings").setOnMenuItemClickListener(this);
        settingsMenu.setIcon(android.R.drawable.ic_menu_preferences);
        aboutMenu = menu.add("About").setOnMenuItemClickListener(this);
        return true;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if (item == settingsMenu) {
            Intent i = new Intent(this, SettingsActivity.class);
            startActivity(i);
        } else if (item == refreshMenu) {
            refreshRoomInfo();
            refetchAddressBook();
        } else if (item == aboutMenu) {
            SpannableString s =
                    new SpannableString(getString(R.string.aboutInfo));
            Linkify.addLinks(s, Linkify.ALL);

            Builder aboutBuilder = new AlertDialog.Builder(this);
            aboutBuilder.setTitle(R.string.aboutTitle);
            aboutBuilder.setMessage(s);
            aboutBuilder.setNegativeButton(R.string.close, null);
            alertDialog = aboutBuilder.show();

            //	Makes links clickable.
            ((TextView) alertDialog.findViewById(android.R.id.message))
                    .setMovementMethod(LinkMovementMethod.getInstance());
        }
        return true;
    }

    @Override
    public void roomListUpdated(Vector<Room> rooms) {
        HashSet<String> hiddenRooms =
                PreferenceManager.getInstance(this).getUnselectedRooms();

        //proceed to requesting room reservation data
        for (Room r : rooms) {
            // Maybe: Move the filtering to the DataProxy
            if (hiddenRooms.contains(r.getName())) {
                continue;
            }
            updateLoadingWindow(1);
            if (proxy == null) {
                proxy = this.getResApplication().getDataProxy();
            } if (proxy != null) {
                proxy.refreshRoomReservations(r);
            }
        }
        updateLoadingWindow(-1);
    }

    @Override
    public void roomReservationsUpdated(final Room room) {
        processRoom(room);
        updateLoadingWindow(-1);
    }

    /*	This is never used?
     *
     **/
    @Override
    public void refreshFailed(ReservatorException e) {
    /*
        if (alertDialog != null) {
			Toast.makeText(this, "dismissed an alert", Toast.LENGTH_SHORT)
			.show();
			alertDialog.dismiss();
		}

		hideLoading();
		Builder alertBuilder = new AlertDialog.Builder(this);
		alertDialog = alertBuilder.setTitle("Error")
			.setMessage(e.getMessage())
			.show();
		Toast.makeText(this, "created an alert", Toast.LENGTH_SHORT).show();
		*/
    }

    private void processRoom(Room r) {
        LobbyReservationRowView v =
                new LobbyReservationRowView(LobbyActivity.this);
        if (v.getException() != null) {
            Log.d("LobbyReservator",
                  "Exception in LobbyReservator: " + v.getException()
                          .getMessage());
            // show only one dialog at time
            if (alertDialog == null || !alertDialog.isShowing()) {
                if (alertDialog != null) {
                    alertDialog.dismiss();
                }
                Builder alertBuilder = new AlertDialog.Builder(this);
                alertBuilder.setTitle("Error!");
                alertBuilder.setMessage(v.getException().getMessage());
                alertDialog = alertBuilder.show();
            }
        }

        v.setRoom(r);
        v.setOnReserveCallback(new OnReserveListener() {
            @Override
            public void call(LobbyReservationRowView v) {
                refreshRoomInfo();
            }
        });

        // This is ugly, adding views in order.
        Comparator<Room> roomCmp = new RoomNameComparator();

        int roomCount = container.getChildCount();
        boolean added = false;
        for (int index = 0; index < roomCount; index++) {
            Room r2 = ((LobbyReservationRowView) container.getChildAt(index))
                    .getRoom();

            // Log.d("Lobby", "sorting: " + r.getName() + ":" + r.isFree() +
            // " -- " + r2.getName() + ":" + r2.isFree());

            if (r.equals(r2)) {
                Log.d("LobbyActivity", "duplicate room -- " + r.getEmail());
                // XXX: minor logic error; same room
                // someone else requested also an update, and we got rooms twice
                container.removeViewAt(index);
                container.addView(v, index);
                added = true;
                break;
            } else if (roomCmp.compare(r, r2) < 0) {
                container.addView(v, index);
                added = true;
                break;
            }
        }
        if (!added) {
            container.addView(v);
        }
    }

    @Override
    public void addressBookUpdated() {
        if (waitingAddresses) {
            waitingAddresses = false;
            updateLoadingWindow(-1);
        }
    }

    @Override
    public void addressBookUpdateFailed(ReservatorException e) {
        if (waitingAddresses) {
            waitingAddresses = false;
            updateLoadingWindow(-1);
        }
    }

    private void refetchAddressBook() {
        waitingAddresses = true;
        updateLoadingWindow(+1);
        ab.refetchEntries();
    }

    private class RoomNameComparator implements Comparator<Room> {
        private Collator collator = Collator.getInstance();

        @Override
        public int compare(Room room1, Room room2) {
            return collator.compare(room1.getName(), room2.getName());
        }
    }
}
