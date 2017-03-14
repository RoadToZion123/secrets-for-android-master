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

import net.tawacentral.roger.secrets.Secret.LogEntry;

import android.app.ListActivity;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.ArrayAdapter;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;


public class AccessLogActivity extends ListActivity {

  public static final String LOG_TAG = "AccessLogActivity";

  private static final long ONE_MINUTE_IN_SECS = 60;
  private static final long ONE_HOUR_IN_SECS = 3600;

  private boolean allowNextResume;


  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    Secret secret = (Secret) getIntent().getExtras().getSerializable(
        SecretsListActivity.EXTRA_ACCESS_LOG);


    String pattern = getText(R.string.log_name_format).toString();
    setTitle(MessageFormat.format(pattern, secret.getDescription()));


    List<Secret.LogEntry> accessLog = secret.getAccessLog();
    ArrayList<String> strings = new ArrayList<String>(accessLog.size());

    for (Secret.LogEntry entry : accessLog) {
      String s = getElapsedString(this, entry, 0);
      strings.add(s);
    }

    setListAdapter(new ArrayAdapter<String>(this, R.layout.access_log,
                                            strings));
    allowNextResume = true;
  }

  @Override
  protected void onResume() {
    super.onResume();


    if (!allowNextResume) {
      Log.d(LOG_TAG, "onResume not allowed");
      finish();
      return;
    }

    allowNextResume = false;
  }


  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (KeyEvent.KEYCODE_BACK == keyCode)
      setResult(RESULT_OK);

    return super.onKeyDown(keyCode, event);
  }


  public static String getElapsedString(Context context, LogEntry entry,
                                        long now) {
    Calendar c = Calendar.getInstance();


    if (0 == now)
      now = c.getTimeInMillis();
    else
      c.setTimeInMillis(now);


    c.set(Calendar.HOUR_OF_DAY, 0);
    c.set(Calendar.MINUTE, 0);
    c.set(Calendar.SECOND, 0);
    c.set(Calendar.MILLISECOND, 0);
    long midnight = c.getTimeInMillis();

    c.add(Calendar.DAY_OF_YEAR, -1);
    long yesterdayMidnight = c.getTimeInMillis();

    long time = entry.getTime();
    long diff = (now - time) / 1000;
    String verb;

    switch(entry.getType()) {
      case LogEntry.CREATED:
        verb = context.getText(R.string.log_created).toString();
        break;
      case LogEntry.CHANGED:
        verb = context.getText(R.string.log_changed).toString();
        break;
      case LogEntry.VIEWED:
        verb = context.getText(R.string.log_viewed).toString();
        break;
      case LogEntry.EXPORTED:
        verb = context.getText(R.string.log_exported).toString();
        break;
      case LogEntry.SYNCED:
        verb = context.getText(R.string.log_synced).toString();
        break;
      default:

        verb = "?";
    }

    String s;
    if (diff < ONE_MINUTE_IN_SECS) {
      String pattern = context.getText(R.string.log_sec).toString();
       s = MessageFormat.format(pattern, verb);
    } else if (diff < ONE_HOUR_IN_SECS) {
      String pattern = context.getText(R.string.log_min).toString();
      s = MessageFormat.format(pattern, verb, diff / 60);
    } else {
      String pattern;
      if (time > midnight) {
        pattern = context.getText(R.string.log_today).toString();
      } else if (time > yesterdayMidnight) {
        pattern = context.getText(R.string.log_yesterday).toString();
      } else {
        pattern = context.getText(R.string.log_date).toString();
      }

      s = MessageFormat.format(pattern, verb, new Date(time));
    }

    return s;
  }
}
