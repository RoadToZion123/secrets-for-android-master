// Copyright (c) 2009, Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package net.tawacentral.roger.secrets;

import java.io.File;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import javax.crypto.Cipher;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.SearchManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.ClipboardManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

@SuppressWarnings({"deprecation","javadoc"})
public class SecretsListActivity extends ListActivity {
  private static final int DIALOG_DELETE_SECRET = 1;
  private static final int DIALOG_CONFIRM_RESTORE = 2;
  private static final int DIALOG_IMPORT_SUCCESS = 3;
  private static final int DIALOG_CHANGE_PASSWORD = 4;
  private static final int DIALOG_ENTER_RESTORE_PASSWORD = 5;
  private static final int DIALOG_SYNC = 6;

  private static final int RC_ACCESS_LOG = 1;
  private static final int RC_STORAGE_IMPORT = 2;
  private static final int RC_STORAGE_EXPORT = 3;
  private static final int RC_STORAGE_BACKUP = 4;
  private static final int RC_STORAGE_RESTORE = 5;

  private static final int PROGRESS_ROUNDS_OFFSET = 4;

  private static final String EMPTY_STRING = "";

  public static final String EXTRA_ACCESS_LOG =
      "net.tawacentreal.secrets.accesslog";

  public static final String UPGRADE_URL =
      "https://docs.google.com/document/d/1L0Bsmh5xmbEnpOfnU-Ps9jWShLnuAphRMn2gHWP0ViY/edit";

  public static final String LOG_TAG = "SecretsListActivity";

  public static final String STATE_IS_EDITING = "is_editing";
  public static final String STATE_EDITING_POSITION = "editing_position";
  public static final String STATE_EDITING_DESCRIPTION = "editing_description";
  public static final String STATE_EDITING_USERNAME = "editing_username";
  public static final String STATE_EDITING_PASSWORD = "editing_password";
  public static final String STATE_EDITING_EMAIL = "editing_email";
  public static final String STATE_EDITING_NOTES = "editing_notes";

  private SecretsListAdapter secretsList; // list of secrets
  private Toast toast; // toast used to show password
  private GestureDetector detector; // detects taps and double taps
  private boolean isEditing; // true if changing a secret
  private int editingPosition; // position of item being edited
  private int cmenuPosition; // position of item for cmenu
  private View root; // root of the layout for this activity
  private View edit; // root view for the editing layout
  private File importedFile; // File that was imported
  private boolean isConfigChange; // being destroyed for config change?
  private String restorePoint; // That file that should be restored from
  private OnlineSyncAgent selectedOSA; // currently selected agent

  private boolean allowNextResume; // Allow the next onResume()

  @Override
  public void onCreate(Bundle state) {
    Log.d(LOG_TAG, "SecretsListActivity.onCreate");
    super.onCreate(state);
    setContentView(R.layout.list);
    if (null == LoginActivity.getSecrets()) {
      finish();
      return;
    }

    secretsList =
        new SecretsListAdapter(this, LoginActivity.getSecrets(),
            LoginActivity.getDeletedSecrets());
    setTitle();

    setListAdapter(secretsList);
    getListView().setTextFilterEnabled(true);

    AutoCompleteTextView username =
        (AutoCompleteTextView) findViewById(R.id.list_username);
    AutoCompleteTextView email =
        (AutoCompleteTextView) findViewById(R.id.list_email);

    username.setAdapter(secretsList.getUsernameAutoCompleteAdapter());
    email.setAdapter(secretsList.getEmailAutoCompleteAdapter());

    root = findViewById(R.id.list_container);
    edit = findViewById(R.id.edit_layout);

    setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);

    if (null != state) {
      isEditing = state.getBoolean(STATE_IS_EDITING);
      if (isEditing) {
        EditText description = (EditText) findViewById(R.id.list_description);
        EditText password = (EditText) findViewById(R.id.list_password);
        EditText notes = (EditText) findViewById(R.id.list_notes);

        editingPosition = state.getInt(STATE_EDITING_POSITION);
        description.setText(state.getCharSequence(STATE_EDITING_DESCRIPTION));
        username.setText(state.getCharSequence(STATE_EDITING_USERNAME));
        password.setText(state.getCharSequence(STATE_EDITING_PASSWORD));
        email.setText(state.getCharSequence(STATE_EDITING_EMAIL));
        notes.setText(state.getCharSequence(STATE_EDITING_NOTES));

        getListView().setVisibility(View.GONE);
        edit.setVisibility(View.VISIBLE);
      }
    }

    if (OS.supportsScrollWheel()) {
      getListView().setOnItemClickListener(new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position,
            long id) {
          onItemClicked(position);
        }
      });
    }

    GestureDetector.SimpleOnGestureListener listener =
        new GestureDetector.SimpleOnGestureListener() {
      @Override
      public boolean onSingleTapConfirmed(MotionEvent e) {
        int position = getListView().pointToPosition((int) e.getX(),
                (int) e.getY());
        onItemClicked(position);
        return true;
      }

      @Override
      public boolean onDoubleTap(MotionEvent e) {
        int position = getListView().pointToPosition((int) e.getX(),
            (int) e.getY());
        if (AdapterView.INVALID_POSITION != position) {
          SetEditViews(position);
          animateToEditView();
          hideToast();
        }
        return true;
      }
    };
    detector = new GestureDetector(this, listener);
    detector.setOnDoubleTapListener(listener);

    getListView().setOnTouchListener(new OnTouchListener() {
      @Override
      public boolean onTouch(View arg0, MotionEvent event) {
        return detector.onTouchEvent(event);
      }
    });

    registerForContextMenu(getListView());
    allowNextResume = true;
  }

  private void onItemClicked(int position) {
    if (AdapterView.INVALID_POSITION != position) {
      Secret secret = getSecret(position);
      CharSequence password = secret.getPassword(false);
      if (password.length() == 0)
        password = getText(R.string.no_password);
      showToast(password);
    }
  }

  @Override
  protected void onNewIntent(Intent intent) {
    setIntent(intent);
    String filter = intent.getStringExtra(SearchManager.QUERY);
    if (filter.charAt(0) != SecretsListAdapter.DOT)
      filter = SecretsListAdapter.DOT + filter;

    getListView().setFilterText(filter);

    getListView().requestFocus();
    OS.hideSoftKeyboard(this, getListView());

    allowNextResume = true;
  }

  @Override
  public boolean onSearchRequested() {
    return isEditing || super.onSearchRequested();
  }

  public void setTitle() {
    CharSequence title;
    int allCount = secretsList.getAllSecrets().size();
    long lastSaved = FileUtils.getTimeOfLastOnlineBackup(this);
    String template = getText(R.string.last_saved_time_format).toString();
    String lastSavedString = lastSaved == 0 ? "" : MessageFormat.format(
        template, new Date(lastSaved));
    int count = secretsList.getCount();
    if (allCount > 0) {
      if (allCount != count) {
        template = getText(R.string.list_title_filtered).toString();
        title = MessageFormat
            .format(template, count, allCount, lastSavedString);
      } else {
        template = getText(R.string.list_title).toString();
        title = MessageFormat.format(template, allCount, lastSavedString);
      }
    } else {
      title = getText(R.string.list_no_data);
    }

    setTitle(title);
  }

  @Override
  protected void onResume() {
    Log.d(LOG_TAG, "SecretsListActivity.onResume");
    super.onResume();

    OnlineAgentManager.sendRollCallBroadcast(this);

    if (!allowNextResume) {
      Log.d(LOG_TAG, "onResume not allowed");
      finish();
      return;
    }

    allowNextResume = false;

    if (0 == secretsList.getAllSecrets().size() && !isEditing) {
      showToast(getText(R.string.list_no_data));
      getListView().post(new Runnable() {
        @Override
        public void run() {
          openOptionsMenu();
        }
      });
    } else if (FileUtils.isOnlineBackupTooOld(this)) {
      getListView().post(new Runnable() {
        @Override
        public void run() {
          showModalDialog(R.string.list_menu_backup,
              R.string.enable_online_backup, R.string.dialog_learn_how, 0,
              R.string.dialog_not_now, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                  if (which == DialogInterface.BUTTON_POSITIVE) {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse(UPGRADE_URL));
                    startActivity(intent);
                  }
                }
              });
        }
      });
    }
  }

  private void showModalDialog(int title, int message, int pos, int neut,
      int neg, DialogInterface.OnClickListener callback) {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setMessage(message);
    builder.setTitle(title);
    if (pos != 0)
      builder.setPositiveButton(pos, callback);
    if (neut != 0)
      builder.setNeutralButton(neut, callback);
    if (neg != 0)
      builder.setNegativeButton(neg, callback);
    AlertDialog dialog = builder.create();
    dialog.show();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.list_menu, menu);

    OS.configureSearchView(this, menu);
    return true;
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {

    boolean secretsListEmpty = (secretsList == null) || secretsList.isEmpty();
    menu.findItem(R.id.list_add).setVisible(!isEditing);
    menu.findItem(R.id.list_backup).setVisible(!isEditing && !secretsListEmpty);
    menu.findItem(R.id.list_search).setVisible(!isEditing);
    menu.findItem(R.id.list_restore).setVisible(!isEditing);
    menu.findItem(R.id.list_sync).setVisible(!isEditing);
    menu.findItem(R.id.list_import).setVisible(!isEditing);
    menu.findItem(R.id.list_export).setVisible(!isEditing && !secretsListEmpty);
    menu.findItem(R.id.list_menu_change_password).setVisible(!isEditing);

    menu.findItem(R.id.list_save).setVisible(isEditing);
    menu.findItem(R.id.list_generate_password).setVisible(isEditing);
    menu.findItem(R.id.list_discard).setVisible(isEditing);

    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    boolean handled = false;
    switch (item.getItemId()) {
    case R.id.list_add:
      SetEditViews(AdapterView.INVALID_POSITION);
      animateToEditView();
      break;
    case R.id.list_search:
      onSearchRequested();
      break;
    case R.id.list_save:
      saveSecret();
    case R.id.list_discard:
      animateFromEditView();
      break;
    case R.id.list_generate_password: {
      String pwd = generatePassword();
      EditText password = (EditText) findViewById(R.id.list_password);
      password.setText(pwd);
      break;
    }
    case R.id.list_backup:
      backupSecrets();
      break;
    case R.id.list_restore:
      if (!OS.ensureStoragePermission(this, RC_STORAGE_RESTORE)) {
        allowNextResume = true;
        break;
      }

      showDialog(DIALOG_CONFIRM_RESTORE);
      break;
    case R.id.list_sync:
      syncRequested();
      break;
    case R.id.list_export:
      exportSecrets();
      break;
    case R.id.list_import:
      importSecrets();
      break;
    case R.id.list_menu_change_password:
      showDialog(DIALOG_CHANGE_PASSWORD);
      break;
    default:
      break;
    }

    return handled;
  }

  @Override
  public void onCreateContextMenu(ContextMenu menu, View v,
      ContextMenuInfo menuInfo) {
    hideToast();
    AdapterView.AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
    cmenuPosition = info.position;
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.list_cmenu, menu);
    Secret secret = secretsList.getSecret(cmenuPosition);
    menu.setHeaderTitle(secret.getDescription());
  }

  @Override
  public boolean onContextItemSelected(MenuItem item) {
    boolean handled = false;
    switch (item.getItemId()) {
    case R.id.list_edit:
      SetEditViews(cmenuPosition);
      animateToEditView();
      break;
    case R.id.list_delete:
      if (AdapterView.INVALID_POSITION != cmenuPosition) {
        showDialog(DIALOG_DELETE_SECRET);
      }
      break;
    case R.id.list_access: {
      Secret secret = secretsList.getSecret(cmenuPosition);
      Intent intent = new Intent(this, AccessLogActivity.class);
      intent.putExtra(EXTRA_ACCESS_LOG, secret);
      startActivityForResult(intent, RC_ACCESS_LOG);
      allowNextResume = true;
      break;
    }
    case R.id.list_copy_password_to_clipboard:
    case R.id.list_copy_username_to_clipboard: {
      Secret secret = secretsList.getSecret(cmenuPosition);
      ClipboardManager cm =
          (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
      int typeId;
      if (item.getItemId() == R.id.list_copy_password_to_clipboard) {
        cm.setText(secret.getPassword(false));
        typeId = R.string.password_copied_to_clipboard;
      } else {
        cm.setText(secret.getUsername());
        typeId = R.string.username_copied_to_clipboard;
      }
      String template = getText(R.string.copied_to_clipboard).toString();
      String typeOfCopy = getText(typeId).toString();
      String msg = MessageFormat.format(template, secret.getDescription(),
          typeOfCopy);
      showToast(msg);
      break;
    }
    }

    return handled;
  }

  @Override
  protected void onActivityResult(int requestCode,
                                  int resultCode,
                                  Intent data) {
    if (requestCode == RC_ACCESS_LOG) {
      if (resultCode != RESULT_OK)
        finish();
    } else {
      finish();
    }
  }

  @Override
  public void onRequestPermissionsResult(int code,
                                         String[] permissions,
                                         int[] grantResults) {
    if (grantResults.length == 1 &&
        grantResults[0] != PackageManager.PERMISSION_GRANTED) {
      return;
    }

    switch (code) {
      case RC_STORAGE_BACKUP:
        backupSecrets();
        break;
      case RC_STORAGE_RESTORE:
        showDialog(DIALOG_CONFIRM_RESTORE);
        break;
      case RC_STORAGE_EXPORT:
        exportSecrets();
        break;
      case RC_STORAGE_IMPORT:
        importSecrets();
        break;
      default:
        Log.e(LOG_TAG, "onRequestPermissionsResult: invalid code=" + code);
        break;
    }
  }

  private void importSecrets() {
    if (!OS.ensureStoragePermission(this, RC_STORAGE_IMPORT)) {
      allowNextResume = true;
      return;
    }

    importedFile = FileUtils.getFileToImport();
    if (null == importedFile) {
      String template = getText(R.string.import_not_found).toString();
      String msg = MessageFormat.format(template, FileUtils.getCsvFileNames());
      showToast(msg);
      return;
    }

    ArrayList<Secret> secrets = new ArrayList<Secret>();
    boolean allSucceeded = FileUtils.importSecrets(this, importedFile, secrets);

    if (!secrets.isEmpty()) {
      for (Secret secret : secrets) {
        secretsList.insert(secret);
      }

      secretsList.notifyDataSetChanged();
      setTitle();

      if (allSucceeded) {
        showDialog(DIALOG_IMPORT_SUCCESS);
      } else {
        String template = getText(R.string.import_partial).toString();
        String msg = MessageFormat.format(template, importedFile.getName());
        showToast(msg);
      }
    } else {
      String template = getText(R.string.import_failed).toString();
      String msg = MessageFormat.format(template, importedFile.getName());
      showToast(msg);
    }
  }

  private void deleteImportedFile() {
    if (null != importedFile) {
      importedFile.delete();
      importedFile = null;
    }
  }

  private void exportSecrets() {
    if (!OS.ensureStoragePermission(this, RC_STORAGE_EXPORT)) {
      allowNextResume = true;
      return;
    }

    if (FileUtils.exportSecrets(this, secretsList.getAllSecrets())) {
      showToast(R.string.export_succeeded);
    } else {
      showToast(R.string.export_failed);
    }
  }

  private boolean restoreSecrets(String rp, SecurityUtils.CipherInfo info,
      boolean askForPassword) {
    ArrayList<Secret> secrets = FileUtils.loadSecrets(this, rp, info);
    if (null == secrets) {
      if (askForPassword) {
        restorePoint = rp;
        showDialog(DIALOG_ENTER_RESTORE_PASSWORD);
      }

      return false;
    }

    LoginActivity.replaceSecrets(secrets);
    secretsList.notifyDataSetChanged();
    setTitle();
    return true;
  }

  private void backupSecrets() {
    if (!OS.ensureStoragePermission(this, RC_STORAGE_BACKUP)) {
      allowNextResume = true;
      return;
    }

    Cipher cipher = SecurityUtils.getEncryptionCipher();
    byte[] salt = SecurityUtils.getSalt();
    int rounds = SecurityUtils.getRounds();
    if (FileUtils.backupSecrets(this, cipher, salt, rounds,
        secretsList.getAllAndDeletedSecrets())) {
      showToast(R.string.backup_succeeded);
    } else {
      showToast(R.string.error_save_secrets);
    }
  }
  private class RestoreDialogState {
    public int selected = 0;

    private List<String> restorePoints;

    public CharSequence[] getRestoreChoices() {
      restorePoints = FileUtils.getRestorePoints(SecretsListActivity.this);
      return restorePoints.toArray(new CharSequence[restorePoints.size()]);
    }

    public String getSelectedRestorePoint() {
      return restorePoints.get(selected);
    }
  }

  private void syncRequested() {
    if (OnlineAgentManager.isActive()) {
      DialogInterface.OnClickListener dialogClickListener =
          new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
              switch (which) {
              case DialogInterface.BUTTON_POSITIVE:
                OnlineAgentManager.cancel();
                break;

              case DialogInterface.BUTTON_NEGATIVE:
                break;
              }
            }
          };
      AlertDialog.Builder builder = new AlertDialog.Builder(this);
      builder.setMessage(R.string.sync_active)
          .setPositiveButton(R.string.yes_option, dialogClickListener)
          .setNegativeButton(R.string.no_option, dialogClickListener).show();
    } else if (normalizeSecrets(false)) { // test if we need to remove dups etc
      DialogInterface.OnClickListener dialogClickListener =
          new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
              switch (which) {
              case DialogInterface.BUTTON_POSITIVE:
                // Yes button clicked
                normalizeSecrets(true); // actually remove dups etc.
                syncRequested();
                break;

              case DialogInterface.BUTTON_NEGATIVE:
                // No button clicked
                break;
              }
            }
          };
      AlertDialog.Builder builder = new AlertDialog.Builder(this);
      builder.setMessage(R.string.sync_normalization)
          .setPositiveButton(R.string.yes_option, dialogClickListener)
          .setNegativeButton(R.string.no_option, dialogClickListener).show();
    } else {
      Collection<OnlineSyncAgent> agents =
          OnlineAgentManager.getAvailableAgents();
      if (agents.size() > 1) {
        showDialog(DIALOG_SYNC);
      } else if (agents.size() == 1) {
        if (!OnlineAgentManager.sendSecrets(agents.iterator().next(),
            secretsList.getAllAndDeletedSecrets(), SecretsListActivity.this)) {
          showToast(R.string.error_osa_secrets);
        }
      } else {
        showToast(R.string.no_osa_available);
      }
    }
  }

  private boolean normalizeSecrets(boolean action) {
    ArrayList<Secret> secrets = LoginActivity.getSecrets();
    ArrayList<String> newDescrs = new ArrayList<String>();

    String lastDescr = "";
    int incr = 1;
    boolean changed = false;

    for (Secret secret : secrets) {
      String descr = secret.getDescription().trim();
      if (descr.equals(lastDescr)) {
        descr = descr + " ##" + incr++;
      }
      if (!secret.getDescription().equals(descr)) {
        if (action) {
          secret.setDescription(descr);
        }
        changed = true;
        newDescrs.add(descr);
        if (incr == 1) {
          lastDescr = descr;
        }
      } else {
        lastDescr = descr;
        incr = 1;
      }
    }

    if (changed && action) {
      ArrayList<Secret> deletedSecrets = LoginActivity.getDeletedSecrets();
      if (deletedSecrets.size() > 0) {
        for (int i = deletedSecrets.size()-1; i > -1 ; i--) {
          Secret deletedSecret = deletedSecrets.get(i);
          if (newDescrs.contains(deletedSecret.getDescription())) {
            deletedSecrets.remove(i);
          }
        }
      }
      secretsList.notifyDataSetChanged();
      String template = getText(R.string.num_normalized).toString();
      showToast(MessageFormat.format(template, newDescrs.size()));
    }

    return changed;
  }

  public void syncSecrets(ArrayList<Secret> changedSecrets, String agentName) {
    Log.d(LOG_TAG, "SecretsListActivity.syncSecrets, secrets: "
        + (changedSecrets == null ? changedSecrets : changedSecrets.size()));
    String template;
    if (changedSecrets != null) {
      secretsList.syncSecrets(changedSecrets);
      getListView().clearTextFilter();
      setTitle();
      template = getText(R.string.sync_succeeded).toString();
    } else {
      template = getText(R.string.sync_failed).toString();
    }
    showToast(MessageFormat.format(template, agentName));
  }

  @Override
  public Dialog onCreateDialog(final int id) {
    Log.d(LOG_TAG, "SecretsListActivity.onCreateDialog, id=" + id);
    Dialog dialog = null;

    switch (id) {
    case DIALOG_DELETE_SECRET: {
      DialogInterface.OnClickListener listener =
          new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
          if (DialogInterface.BUTTON_POSITIVE == which)
            deleteSecret(cmenuPosition);
        }
      };

      dialog = new AlertDialog.Builder(this)
          .setTitle(R.string.list_menu_delete)
          .setIcon(android.R.drawable.ic_dialog_alert).setMessage(EMPTY_STRING)
          .setPositiveButton(R.string.login_reset_password_pos, listener)
          .setNegativeButton(R.string.login_reset_password_neg, null).create();
      break;
    }
    case DIALOG_CONFIRM_RESTORE: {
      final RestoreDialogState state = new RestoreDialogState();

      DialogInterface.OnClickListener itemListener =
          new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
          state.selected = which;
          dialog.dismiss();
          SecurityUtils.CipherInfo info = SecurityUtils.getCipherInfo();
          if (restoreSecrets(state.getSelectedRestorePoint(), info, true))
            showToast(R.string.restore_succeeded);
        }
      };

      dialog = new AlertDialog.Builder(this)
          .setTitle(R.string.dialog_restore_title)
          .setIcon(android.R.drawable.ic_dialog_alert)
          .setSingleChoiceItems(state.getRestoreChoices(), state.selected,
              itemListener).create();
      break;
    }
    case DIALOG_IMPORT_SUCCESS: {
      DialogInterface.OnClickListener listener =
          new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
          if (DialogInterface.BUTTON1 == which) {
            deleteImportedFile();
          }

          importedFile = null;
        }
      };

      dialog = new AlertDialog.Builder(this)
          .setTitle(R.string.list_menu_import)
          .setIcon(android.R.drawable.ic_dialog_info).setMessage(EMPTY_STRING)
          .setPositiveButton(R.string.login_reset_password_pos, listener)
          .setNegativeButton(R.string.login_reset_password_neg, null).create();
      break;
    }
    case DIALOG_CHANGE_PASSWORD: {
      DialogInterface.OnClickListener listener =
          new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialogi, int which) {
          AlertDialog dialog = (AlertDialog) dialogi;
          TextView password1 = (TextView) dialog.findViewById(R.id.password);
          TextView password2 = (TextView) dialog
              .findViewById(R.id.password_validation);
          String password = password1.getText().toString();
          String p2 = password2.getText().toString();
          if (!password.equals(p2) || password.length() == 0) {
            showToast(R.string.invalid_password);
            return;
          }

          SeekBar bar = (SeekBar) dialog.findViewById(R.id.cipher_strength);
          byte[] salt = SecurityUtils.getSalt();
          int rounds = bar.getProgress() + PROGRESS_ROUNDS_OFFSET;

          SecurityUtils.CipherInfo info = SecurityUtils.createCiphers(password,
              salt, rounds);
          if (null != info) {
            SecurityUtils.saveCiphers(info);
            showToast(R.string.password_changed);
          } else {
            showToast(R.string.error_reset_password);
          }
        }
      };

      LayoutInflater inflater = getLayoutInflater();
      View view = inflater.inflate(R.layout.change_password, getListView(),
          false);

      dialog = new AlertDialog.Builder(this)
          .setTitle(R.string.list_menu_change_password)
          .setIcon(android.R.drawable.ic_dialog_info).setView(view)
          .setPositiveButton(R.string.list_menu_change_password, listener)
          .create();
      final Dialog dialogFinal = dialog;

      SeekBar bar = (SeekBar) view.findViewById(R.id.cipher_strength);
      bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress,
            boolean fromUser) {
          setCipherStrengthLabel(dialogFinal,
                                 progress + PROGRESS_ROUNDS_OFFSET);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
      });
      break;
    }
    case DIALOG_ENTER_RESTORE_PASSWORD: {
      DialogInterface.OnClickListener listener =
          new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialogi, int which) {
          AlertDialog dialog = (AlertDialog) dialogi;
          TextView password1 = (TextView) dialog.findViewById(R.id.password);

          String password = password1.getText().toString();
          FileUtils.SaltAndRounds saltAndRounds = FileUtils.getSaltAndRounds(
              null, restorePoint);

          String message = null;

          SecurityUtils.CipherInfo info = SecurityUtils.createCiphers(password,
              saltAndRounds.salt, saltAndRounds.rounds);
          if (restoreSecrets(restorePoint, info, false)) {
            SecurityUtils.clearCiphers();
            SecurityUtils.saveCiphers(info);
            message = getText(R.string.password_changed).toString();
            message += '\n';
            message += getText(R.string.restore_succeeded).toString();
          } else {
            ArrayList<Secret> secrets =
                FileUtils.loadSecretsV3(SecretsListActivity.this, info,
                                        restorePoint);

            if (secrets == null) {
              Cipher cipher2 = SecurityUtils.createDecryptionCipherV2(password,
                  saltAndRounds.salt, saltAndRounds.rounds);
              secrets = FileUtils.loadSecretsV2(
                  SecretsListActivity.this, restorePoint, cipher2,
                  saltAndRounds.salt, saltAndRounds.rounds);
            }

            if (secrets == null) {
              Cipher cipher1 = SecurityUtils.createDecryptionCipherV1(password);
              secrets = FileUtils.loadSecretsV1(SecretsListActivity.this,
                  cipher1, restorePoint);
            }

            if (secrets != null) {
              LoginActivity.replaceSecrets(secrets);
              secretsList.notifyDataSetChanged();
              setTitle();
              message = getText(R.string.restore_succeeded).toString();
            }
          }

          if (message == null)
            message = getText(R.string.restore_failed).toString();

          showToast(message);
        }
      };

      LayoutInflater inflater = getLayoutInflater();
      View view = inflater.inflate(R.layout.change_password, getListView(),
          false);

      view.findViewById(R.id.cipher_strength).setVisibility(View.GONE);
      view.findViewById(R.id.cipher_strength_label).setVisibility(View.GONE);
      view.findViewById(R.id.password_validation).setVisibility(View.GONE);
      view.findViewById(R.id.password_validation_label)
          .setVisibility(View.GONE);

      dialog = new AlertDialog.Builder(this)
          .setTitle(R.string.login_enter_password)
          .setIcon(android.R.drawable.ic_dialog_info).setView(view)
          .setPositiveButton(R.string.list_menu_restore, listener).create();
      break;
    }
    case DIALOG_SYNC: {
      Log.d(LOG_TAG, "Showing sync selection dialog");
      DialogInterface.OnClickListener itemListener =
          new DialogInterface.OnClickListener() {

        public void onClick(DialogInterface dialog, int which) {
          AlertDialog alertDialog = (AlertDialog) dialog;
          selectedOSA = (OnlineSyncAgent) alertDialog.getListView()
              .getItemAtPosition(which);
          Log.d(LOG_TAG, "Selected app: " + selectedOSA.getDisplayName());
          dialog.dismiss();

          if (!OnlineAgentManager.sendSecrets(selectedOSA,
              secretsList.getAllAndDeletedSecrets(), SecretsListActivity.this)) {
            showToast(R.string.error_osa_secrets);
          }
        }
      };

      OnlineAgentAdapter adapter = new OnlineAgentAdapter(
          SecretsListActivity.this,
          android.R.layout.select_dialog_singlechoice, android.R.id.text1);
      String title = getString(R.string.dialog_sync_title);
      dialog = new AlertDialog.Builder(this).setTitle(title)
          .setIcon(android.R.drawable.ic_dialog_alert)
          .setSingleChoiceItems(adapter, 0, itemListener).create();
      break;
    }
    default:
      break;
    }

    return dialog;
  }

  private void setCipherStrengthLabel(Dialog dialog, int rounds) {
    String template = getText(R.string.cipher_strength_label).toString();
    String msg = MessageFormat.format(template, rounds);
    TextView text = (TextView) dialog.findViewById(R.id.cipher_strength_label);
    text.setText(msg);
  }

  @Override
  protected void onPrepareDialog(int id, Dialog dialog) {
    super.onPrepareDialog(id, dialog);

    switch (id) {
    case DIALOG_DELETE_SECRET: {
      AlertDialog alert = (AlertDialog) dialog;
      Secret secret = secretsList.getSecret(cmenuPosition);
      String template = getText(R.string.edit_menu_delete_secret_message)
          .toString();
      String msg = MessageFormat.format(template, secret.getDescription());
      alert.setMessage(msg);
      break;
    }
    case DIALOG_IMPORT_SUCCESS: {
      AlertDialog alert = (AlertDialog) dialog;
      String template = getText(R.string.edit_menu_import_secrets_message)
          .toString();
      String msg = MessageFormat.format(template, importedFile.getName());
      alert.setMessage(msg);
      break;
    }
    case DIALOG_CHANGE_PASSWORD: {
      SeekBar bar = (SeekBar) dialog.findViewById(R.id.cipher_strength);
      int rounds = SecurityUtils.getRounds();
      bar.setProgress(rounds - PROGRESS_ROUNDS_OFFSET);
      setCipherStrengthLabel(dialog, rounds);
      TextView password1 = (TextView) dialog.findViewById(R.id.password);
      password1.setText("");
      TextView password2 = (TextView) dialog
          .findViewById(R.id.password_validation);
      password2.setText("");
      password1.requestFocus();
      break;
    }
    case DIALOG_ENTER_RESTORE_PASSWORD: {
      TextView password1 = (TextView) dialog.findViewById(R.id.password);
      password1.setText("");
      break;
    }
    }
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (isEditing && KeyEvent.KEYCODE_BACK == keyCode) {
      saveSecret();
      animateFromEditView();
      return true;
    }

    return super.onKeyDown(keyCode, event);
  }

  @Override
  protected void onSaveInstanceState(Bundle state) {
    Log.d(LOG_TAG, "SecretsListActivity.onSaveInstanceState");
    super.onSaveInstanceState(state);

    state.putBoolean(STATE_IS_EDITING, isEditing);

    if (isEditing) {
      saveSecret();

      EditText description = (EditText) findViewById(R.id.list_description);
      EditText username = (EditText) findViewById(R.id.list_username);
      EditText password = (EditText) findViewById(R.id.list_password);
      EditText email = (EditText) findViewById(R.id.list_email);
      EditText notes = (EditText) findViewById(R.id.list_notes);

      state.putInt(STATE_EDITING_POSITION, editingPosition);
      state.putCharSequence(STATE_EDITING_DESCRIPTION, description.getText());
      state.putCharSequence(STATE_EDITING_USERNAME, username.getText());
      state.putCharSequence(STATE_EDITING_PASSWORD, password.getText());
      state.putCharSequence(STATE_EDITING_EMAIL, email.getText());
      state.putCharSequence(STATE_EDITING_NOTES, notes.getText());
    }

    Log.d(LOG_TAG, "SecretsListActivity.onSaveInstanceState");
  }

  /** Called when the activity is no longer visible. */
  @Override
  protected void onPause() {
    Log.d(LOG_TAG, "SecretsListActivity.onPause");

    if (null != toast)
      toast.cancel();

    ArrayList<Secret> secrets = secretsList.getAllAndDeletedSecrets();

    Cipher cipher = SecurityUtils.getEncryptionCipher();
    byte[] salt = SecurityUtils.getSalt();
    int rounds = SecurityUtils.getRounds();
    SaveService.execute(this, secrets, cipher, salt, rounds);
    super.onPause();
  }

  @Override
  public Object onRetainNonConfigurationInstance() {
    Log.d(LOG_TAG, "SecretsListActivity.onRetainNonConfigurationInstance");
    isConfigChange = true;
    return super.onRetainNonConfigurationInstance();
  }


  @Override
  protected void onDestroy() {
    if (!isConfigChange) {
      Log.d(LOG_TAG, "SecretsListActivity.onDestroy");
      LoginActivity.clearSecrets();
    }

    super.onDestroy();
  }

  private void SetEditViews(int position) {
    editingPosition = position;

    EditText description = (EditText) findViewById(R.id.list_description);
    EditText username = (EditText) findViewById(R.id.list_username);
    EditText password = (EditText) findViewById(R.id.list_password);
    EditText email = (EditText) findViewById(R.id.list_email);
    EditText notes = (EditText) findViewById(R.id.list_notes);

    if (AdapterView.INVALID_POSITION == position) {
      description.setText(EMPTY_STRING);
      username.setText(EMPTY_STRING);
      password.setText(EMPTY_STRING);
      email.setText(EMPTY_STRING);
      notes.setText(EMPTY_STRING);

      description.requestFocus();
    } else {
      Secret secret = secretsList.getSecret(position);

      description.setText(secret.getDescription());
      username.setText(secret.getUsername());
      password.setText(secret.getPassword(false));
      email.setText(secret.getEmail());
      notes.setText(secret.getNote());

      password.requestFocus();
    }

    ScrollView scroll = (ScrollView) findViewById(R.id.edit_layout);
    scroll.scrollTo(0, 0);
  }

  private void saveSecret() {
    EditText description = (EditText) findViewById(R.id.list_description);
    EditText username = (EditText) findViewById(R.id.list_username);
    EditText password = (EditText) findViewById(R.id.list_password);
    EditText email = (EditText) findViewById(R.id.list_email);
    EditText notes = (EditText) findViewById(R.id.list_notes);
    Secret secret;
    String description_text = description.getText().toString();
    String username_text = username.getText().toString();
    String password_text = password.getText().toString();
    String email_text = email.getText().toString();
    String note_text = notes.getText().toString();

    if (AdapterView.INVALID_POSITION == editingPosition) {
      if (0 == description.getText().length()
          && 0 == username.getText().length()
          && 0 == password.getText().length() && 0 == email.getText().length()
          && 0 == notes.getText().length())
        return;

      secret = new Secret();
    } else {
      secret = secretsList.getSecret(editingPosition);

      if (description_text.equals(secret.getDescription())
          && username_text.equals(secret.getUsername())
          && password_text.equals(secret.getPassword(false))
          && email_text.equals(secret.getEmail())
          && note_text.equals(secret.getNote())) {
        secretsList.notifyDataSetChanged();
        return;
      }

      secretsList.remove(editingPosition);
    }

    secret.setDescription(description.getText().toString());
    secret.setUsername(username.getText().toString());
    secret.setPassword(password.getText().toString(),
        (AdapterView.INVALID_POSITION == editingPosition ? false : true));
    secret.setEmail(email.getText().toString());
    secret.setNote(notes.getText().toString());

    editingPosition = secretsList.insert(secret);
    secretsList.notifyDataSetChanged();
  }

  public void deleteSecret(int position) {
    if (AdapterView.INVALID_POSITION != position) {
      secretsList.delete(position);
      secretsList.notifyDataSetChanged();
      if (isEditing) {
        editingPosition = AdapterView.INVALID_POSITION;
        animateFromEditView();
      } else {
        setTitle();
      }
    }
  }


  private void showToast(int message) {
    showToast(getText(message));
  }

  private void showToast(CharSequence message) {
    if (null == toast) {
      toast = Toast.makeText(SecretsListActivity.this, message,
          Toast.LENGTH_LONG);
      toast.setGravity(Gravity.CENTER, 0, 0);
    } else {
      toast.setText(message);
    }

    toast.show();
  }

  private void hideToast() {
    if (null != toast) {
      toast.cancel();
    }
  }

  private Secret getSecret(int position) {
    return (Secret) getListAdapter().getItem(position);
  }

  private void animateToEditView() {
    assert (!isEditing);
    isEditing = true;

    if (null != toast)
      toast.cancel();

    OS.hideSoftKeyboard(this, getListView());
    OS.invalidateOptionsMenu(this);

    View list = getListView();
    int cx = root.getWidth() / 2;
    int cy = root.getHeight() / 2;
    Animation animation = new Flip3dAnimation(list, edit, cx, cy, true);
    animation.setAnimationListener(new AnimationListener() {
      @Override
      public void onAnimationEnd(Animation animation) {
        hideToast();
        if (0 == secretsList.getCount()) {
          showToast(getText(R.string.edit_instructions));
        }
      }

      @Override
      public void onAnimationRepeat(Animation animation) {
      }

      @Override
      public void onAnimationStart(Animation animation) {
      }
    });

    root.startAnimation(animation);
  }

  private void animateFromEditView() {
    assert (isEditing);
    isEditing = false;

    OS.hideSoftKeyboard(this, getListView());
    OS.invalidateOptionsMenu(this);

    View list = getListView();
    int cx = root.getWidth() / 2;
    int cy = root.getHeight() / 2;
    Animation animation = new Flip3dAnimation(list, edit, cx, cy, false);
    animation.setAnimationListener(new AnimationListener() {
      @Override
      public void onAnimationEnd(Animation animation) {
        if (AdapterView.INVALID_POSITION != editingPosition) {
          ListView listView = getListView();
          listView.requestFocus();

          int first = listView.getFirstVisiblePosition();
          int last = listView.getLastVisiblePosition();
          if (editingPosition < first || editingPosition > last) {
            listView.setSelection(editingPosition);
          }
        }

        setTitle();

        if (1 == secretsList.getAllSecrets().size()) {
          showToast(getText(R.string.list_instructions));
        }
      }

      @Override
      public void onAnimationRepeat(Animation animation) {
      }

      @Override
      public void onAnimationStart(Animation animation) {
      }
    });
    root.startAnimation(animation);
  }

  private String generatePassword() {
    StringBuilder builder = new StringBuilder(8);
    try {
      SecureRandom r = SecureRandom.getInstance("SHA1PRNG");
      final String p = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
          + "abcdefghijklmnopqrstuvwxyz" + "abcdefghijklmnopqrstuvwxyz"
          + "0123456789" + "0123456789" + "~!@#$%^&*()_+`-=[]{}|;':,./<>?";

      for (int i = 0; i < 8; ++i)
        builder.append(p.charAt(r.nextInt(128)));
    } catch (NoSuchAlgorithmException ex) {
      Log.e(LOG_TAG, "generatePassword", ex);
    }
    return builder.toString();
  }
}
