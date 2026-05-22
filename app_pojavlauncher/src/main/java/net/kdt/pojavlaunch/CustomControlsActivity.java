package net.kdt.pojavlaunch;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.drawerlayout.widget.DrawerLayout;

import net.kdt.pojavlaunch.customcontrols.ControlData;
import net.kdt.pojavlaunch.customcontrols.ControlDrawerData;
import net.kdt.pojavlaunch.customcontrols.ControlJoystickData;
import net.kdt.pojavlaunch.customcontrols.ControlLayout;
import net.kdt.pojavlaunch.customcontrols.EditorExitable;
import net.kdt.pojavlaunch.customcontrols.buttons.ControlInterface;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.io.IOException;


public class CustomControlsActivity extends BaseActivity implements EditorExitable {
	private DrawerLayout mDrawerLayout;
	private ListView mDrawerNavigationView;
	private ControlLayout mControlLayout;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_custom_controls);

		mControlLayout = findViewById(R.id.customctrl_controllayout);
		mDrawerLayout = findViewById(R.id.customctrl_drawerlayout);
		mDrawerNavigationView = findViewById(R.id.customctrl_navigation_view);
		View mPullDrawerButton = findViewById(R.id.drawer_button);

		mPullDrawerButton.setOnClickListener(v -> mDrawerLayout.openDrawer(mDrawerNavigationView));
		mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);

		mDrawerNavigationView.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1,getResources().getStringArray(R.array.menu_customcontrol_customactivity)));
		mDrawerNavigationView.setOnItemClickListener((parent, view, position, id) -> {
            switch(position) {
                case 0: mControlLayout.addControlButton(new ControlData("New")); break;
                case 1: mControlLayout.addDrawer(new ControlDrawerData()); break;
                case 2: mControlLayout.addJoystickButton(new ControlJoystickData()); break;
                case 3: mControlLayout.openLoadDialog(); break;
                case 4: mControlLayout.openSaveDialog(null); break;
                case 5: mControlLayout.openSetDefaultDialog(); break;
                case 6: mControlLayout.openSaveDialog(this); break;
                case 7: // Saving the currently shown control
                    try {
                        Uri contentUri = DocumentsContract.buildDocumentUri(getString(R.string.storageProviderAuthorities), mControlLayout.saveToDirectory(mControlLayout.mLayoutFileName));

                        Intent shareIntent = new Intent();
                        shareIntent.setAction(Intent.ACTION_SEND);
                        shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        shareIntent.setType("application/json");
                        startActivity(shareIntent);

                        Intent sendIntent = Intent.createChooser(shareIntent, mControlLayout.mLayoutFileName);
                        startActivity(sendIntent);
                    }catch (Exception e) {
                        Tools.showError(this, e);
                    }
                    break;
                case 8: mControlLayout.openExitDialog(this); break;
			}
			mDrawerLayout.closeDrawers();
		});
		mControlLayout.setModifiable(true);
		try {
			mControlLayout.loadLayout(LauncherPreferences.PREF_DEFAULTCTRL_PATH);
		}catch (IOException e) {
			Tools.showError(this, e);
		}

		net.kdt.pojavlaunch.customcontrols.handleview.EditControlSideDialog.sSkinPickListener = button -> {
			mEditedButtonForSkin = button;
			Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
			intent.setType("image/*");
			startActivityForResult(Intent.createChooser(intent, "Select Button Skin"), 1001);
		};
	}

	private ControlInterface mEditedButtonForSkin;

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == 1001 && resultCode == RESULT_OK && data != null && data.getData() != null) {
			if (mEditedButtonForSkin != null) {
				try {
					Uri selectedImageUri = data.getData();
					java.io.File customSkinsDir = new java.io.File(getFilesDir(), "custom_skins");
					if (!customSkinsDir.exists()) customSkinsDir.mkdirs();
					String extension = ".png";
				String uriPath = selectedImageUri.getPath();
				if (uriPath != null && uriPath.toLowerCase(java.util.Locale.ROOT).endsWith(".gif")) {
					extension = ".gif";
				}
				java.io.File destFile = new java.io.File(customSkinsDir, "skin_" + System.currentTimeMillis() + extension);
					
				java.io.InputStream is = getContentResolver().openInputStream(selectedImageUri);
					java.io.OutputStream os = new java.io.FileOutputStream(destFile);
					byte[] buffer = new byte[1024];
					int length;
					while ((length = is.read(buffer)) > 0) {
						os.write(buffer, 0, length);
					}
					os.flush();
					os.close();
					is.close();

					mEditedButtonForSkin.getProperties().buttonImagePath = destFile.getAbsolutePath();
					mEditedButtonForSkin.setBackground();
				} catch (Exception e) {
					Tools.showError(this, e);
				}
			}
		}
	}

	@Override
	public void onBackPressed() {
		mControlLayout.askToExit(this);
	}

	@Override
	public void exitEditor() {
		super.onBackPressed();
	}
}
