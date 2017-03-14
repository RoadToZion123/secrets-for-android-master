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
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;

import javax.crypto.Cipher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;


public class LoginActivity extends Activity implements TextWatcher {

  private static final int DIALOG_RESET_PASSWORD = 1;

  public static final String LOG_TAG = "LoginActivity";


  private static ArrayList<Secret> secrets;

  private static ArrayList<Secret> deletedSecrets;

  private boolean isFirstRun;
  private boolean isValidatingPassword;
  private String passwordString;
  private Toast toast;


  @Override
  public void onCreate(Bundle savedInstanceState) {
    Log.d(LOG_TAG, "LoginActivity.onCreate");
    super.onCreate(savedInstanceState);
    setContentView(R.layout.login);


    EditText password = (EditText)findViewById(R.id.login_password);
    password.setOnKeyListener(new View.OnKeyListener() {
      @Override
      public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (KeyEvent.KEYCODE_ENTER == keyCode) {
          if (KeyEvent.ACTION_UP == event.getAction())
            handlePasswordClick((TextView) v);

          return true;
        }

        return false;
      }
    });

    password.addTextChangedListener(this);

    FileUtils.cleanupDataFiles(this);
    Log.d(LOG_TAG, "LoginActivity.onCreate done");
  }


  @Override
  protected void onResume() {
    Log.d(LOG_TAG, "LoginActivity.onResume");
    super.onResume();

    isFirstRun = isFirstRun();
    TextView instructions = (TextView)findViewById(R.id.login_instructions);
    TextView strength = (TextView)findViewById(R.id.password_strength);
    TextView password = (TextView) findViewById(R.id.login_password);
    if (isFirstRun) {
      instructions.setText(R.string.login_instruction_1);
      strength.setVisibility(TextView.VISIBLE);
      updatePasswordStrengthView(password.getText().toString());
    } else {
      instructions.setText("");
      strength.setVisibility(TextView.GONE);
    }

    password.setHint(R.string.login_enter_password);
    Log.d(LOG_TAG, "LoginActivity.onResume done");
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.login_menu, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    boolean handled = false;

    switch(item.getItemId()) {
      case R.id.reset_password:
        showDialog(DIALOG_RESET_PASSWORD);
        handled = true;
        break;
      default:
        break;
    }

    return handled;
  }

  @Override
  public Dialog onCreateDialog(int id) {
    Dialog dialog = null;

    switch (id) {
      case DIALOG_RESET_PASSWORD: {
        DialogInterface.OnClickListener listener =
            new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialog, int which) {
                if (DialogInterface.BUTTON_POSITIVE == which) {
                  if (!FileUtils.deleteSecrets(LoginActivity.this)) {
                    showToast(R.string.error_reset_password, Toast.LENGTH_LONG);
                  } else {
                    isValidatingPassword = false;
                    clearSecrets();
                  }

                  onResume();
                }
              }
            };
        dialog = new AlertDialog.Builder(this)
            .setTitle(R.string.login_menu_reset_password)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setMessage(R.string.login_menu_reset_password_message)
            .setPositiveButton(R.string.login_reset_password_pos, listener)
            .setNegativeButton(R.string.login_reset_password_neg, null)
            .create();
        }
        break;
      default:
        break;
    }

    return dialog;
  }

  /** Overrides from TextWatcher */
  @Override
  public void afterTextChanged(Editable s) {
  }
  @Override
  public void beforeTextChanged(
      CharSequence s, int start, int count, int after) {
  }

  @Override
  public void onTextChanged(CharSequence s, int start, int before, int count) {
    Log.d(LOG_TAG, "LoginActivity.onTextChanged");

    updatePasswordStrengthView(s.toString());
  }

  private boolean isFirstRun() {
    return !FileUtils.secretsExist(this);
  }


  private void handlePasswordClick(TextView passwordView) {
    Log.d(LOG_TAG, "LoginActivity.handlePasswordClick");

    if (null != secrets) {
      Log.d(LOG_TAG, "LoginActivity.handlePasswordClick ignoring");
      return;
    }

    String passwordString = passwordView.getText().toString();

    if (isFirstRun) {
      TextView instructions = (TextView)findViewById(R.id.login_instructions);
      TextView strength = (TextView)findViewById(R.id.password_strength);

      if (!isValidatingPassword) {
        instructions.setText(R.string.login_instruction_2);
        passwordView.setHint(R.string.login_validate_password);
        passwordView.setText("");
        strength.setVisibility(TextView.GONE);

        this.passwordString = passwordString;
        isValidatingPassword = true;
        return;
      } else {
        if (!passwordString.equals(this.passwordString)) {
          instructions.setText(R.string.login_instruction_1);
          strength.setVisibility(TextView.VISIBLE);
          passwordView.setHint(R.string.login_enter_password);
          passwordView.setText("");
          showToast(R.string.invalid_password, Toast.LENGTH_SHORT);

          this.passwordString = null;
          isValidatingPassword = false;
          return;
        }
      }
    }

    passwordView.setText("");

    FileUtils.SaltAndRounds pair = FileUtils.getSaltAndRounds(this,
        FileUtils.SECRETS_FILE_NAME);
    SecurityUtils.saveCiphers(SecurityUtils.createCiphers(passwordString,
                                                          pair.salt,
                                                          pair.rounds));

    ArrayList<Secret> loadedSecrets = null;

    if (isFirstRun) {
      loadedSecrets = new ArrayList<Secret>();

      Cipher cipher = SecurityUtils.getEncryptionCipher();
      File file = getFileStreamPath(FileUtils.SECRETS_FILE_NAME);
      byte[] salt = SecurityUtils.getSalt();
      int rounds = SecurityUtils.getRounds();
      int err = FileUtils.saveSecrets(this, file, cipher, salt, rounds,
                                      loadedSecrets);
      if (0 != err) {
        showToast(err, Toast.LENGTH_LONG);
        return;
      }
    } else {
      loadedSecrets = FileUtils.loadSecrets(this);
      if (null == loadedSecrets) {
        loadedSecrets = FileUtils.loadSecretsV3(this);
        if (null == loadedSecrets) {
          Cipher cipher2 = SecurityUtils.createDecryptionCipherV2(
              passwordString, pair.salt, pair.rounds);
          if (null != cipher2) {
            loadedSecrets = FileUtils.loadSecretsV2(this, cipher2, pair.salt,
                                                 pair.rounds);
          }
        }
        if (null == loadedSecrets) {
          Cipher cipher1 =
              SecurityUtils.createDecryptionCipherV1(passwordString);
          if (null != cipher1)
            loadedSecrets = FileUtils.loadSecretsV1(this, cipher1);
        }
        if (null == loadedSecrets) {
          showToast(R.string.invalid_password, Toast.LENGTH_LONG);
          return;
        }

        Collections.sort(loadedSecrets);
      }
    }

    if (secrets == null)
      secrets = new ArrayList<Secret>();

    if (deletedSecrets == null)
      deletedSecrets = new ArrayList<Secret>();


    replaceSecrets(loadedSecrets);

    passwordString = null;
    Intent intent = new Intent(LoginActivity.this, SecretsListActivity.class);
    startActivity(intent);
    Log.d(LOG_TAG, "LoginActivity.handlePasswordClick done");
  }

  private void showToast(int message, int length) {
    if (null == toast) {
      toast = Toast.makeText(LoginActivity.this, message, length);
      toast.setGravity(Gravity.CENTER, 0, 0);
    } else {
      toast.setText(message);
    }

    toast.show();
  }
  private void updatePasswordStrengthView(String password) {
    TextView strengthView = (TextView)findViewById(R.id.password_strength);
    if (TextView.VISIBLE != strengthView.getVisibility())
      return;

    if (password.isEmpty()) {
      strengthView.setText("");
      return;
    }

    PasswordStrength str = PasswordStrength.calculateStrength(password);
    strengthView.setText(MessageFormat.format(
        getText(R.string.password_strength_caption).toString(),
        str.getText(this)));
    strengthView.setTextColor(str.getColor());
  }
  public static ArrayList<Secret> getDeletedSecrets() {
    return deletedSecrets;
  }
  public static ArrayList<Secret> getSecrets() {
    return secrets;
  }
  public static void replaceSecrets(ArrayList<Secret> newSecrets) {

    LoginActivity.secrets.clear();
    LoginActivity.deletedSecrets.clear();
    for (Secret secret : newSecrets) {
      if (secret.isDeleted()) {
        deletedSecrets.add(secret);
      } else {
        secrets.add(secret);
      }
    }
  }
  public static void clearSecrets() {
    secrets = null;
    deletedSecrets = null;
    SecurityUtils.clearCiphers();
  }
}
