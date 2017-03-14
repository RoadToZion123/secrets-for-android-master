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

import java.util.ArrayList;
import java.util.TreeSet;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

public class SecretsListAdapter extends BaseAdapter implements Filterable {
  public static final char DOT = '.';
  private ArrayList<Secret> secrets;
  private final ArrayList<Secret> allSecrets;
  private final ArrayList<Secret> deletedSecrets;
  private TreeSet<String> usernames;
  private TreeSet<String> emails;
  private ArrayAdapter<String> usernameAdapter;
  private ArrayAdapter<String> emailAdapter;
  private SecretsListActivity activity;
  private LayoutInflater inflater;
  private SecretsFilter filter;

  SecretsListAdapter(SecretsListActivity activity, ArrayList<Secret> secrets,
      ArrayList<Secret> deletedSecrets) {
    this.activity = activity;
    inflater = LayoutInflater.from(this.activity);
    allSecrets = secrets;
    this.secrets = allSecrets;
    this.deletedSecrets = deletedSecrets;
    usernameAdapter = new ArrayAdapter<String>(activity,
        android.R.layout.simple_dropdown_item_1line);
    emailAdapter = new ArrayAdapter<String>(activity,
        android.R.layout.simple_dropdown_item_1line);
    usernames = new TreeSet<String>();
    emails = new TreeSet<String>();

    usernameAdapter.setNotifyOnChange(false);
    emailAdapter.setNotifyOnChange(false);

    for (int i = 0; i < allSecrets.size(); ++i) {
      Secret secret = allSecrets.get(i);
      usernames.add(secret.getUsername());
      emails.add(secret.getEmail());
    }

    for (String username : usernames)
      usernameAdapter.add(username);

    for (String email : emails)
      emailAdapter.add(email);

    usernameAdapter.setNotifyOnChange(true);
    emailAdapter.setNotifyOnChange(true);
  }

  @Override
  public boolean areAllItemsEnabled() {
    return true;
  }

  @Override
  public boolean isEnabled(int position) {
    return true;
  }

  @Override
  public int getCount() {
    return secrets.size();
  }

  @Override
  public Object getItem(int position) {
    return getSecret(position);
  }

  @Override
  public long getItemId(int position) {
    return position;
  }

  @Override
  public int getItemViewType(int position) {
    return 0;
  }

  @Override
  public View getView(int position, View convertView, ViewGroup parent) {
    if (null == convertView) {
      convertView = inflater.inflate(
          R.layout.list_item, parent, false);
    }

    Secret secret = secrets.get(position);

    TextView text1 = (TextView) convertView.findViewById(android.R.id.text1);
    TextView text2 = (TextView) convertView.findViewById(android.R.id.text2);

    text1.setText(secret.getDescription());
    text2.setText(getFriendlyId(secret));

    return convertView;
  }

  @Override
  public int getViewTypeCount() {
    return 1;
  }

  @Override
  public boolean hasStableIds() {
    return false;
  }

  @Override
  public boolean isEmpty() {
    return 0 == secrets.size();
  }

  @Override
  public Filter getFilter() {
    if (null == filter)
      filter  = new SecretsFilter();

    return filter;
  }


  private class SecretsFilter extends Filter {
    @Override
    protected FilterResults performFiltering(CharSequence prefix) {

      boolean isFullTextSearch = false;
      FilterResults results = new FilterResults();
      String prefixString = null == prefix ? null
                                           : prefix.toString().toLowerCase();
      ArrayList<Secret> secrets;
      if (null != prefixString) {
        if (prefixString.length() > 0 && prefixString.charAt(0) == DOT) {
          isFullTextSearch = prefixString.length() > 1 &&
              prefixString.charAt(1) != DOT;
          prefixString = prefixString.substring(1);
        }
      }

      if (null != prefixString && prefixString.length() > 0) {
        synchronized (allSecrets) {
          secrets = new ArrayList<Secret>(allSecrets);
        }

        for (int i = secrets.size() - 1; i >= 0; --i) {
          Secret secret = secrets.get(i);
          if (isFullTextSearch) {
            if (!secret.getDescription().toLowerCase().contains(prefixString) &&
                !secret.getEmail().toLowerCase().contains(prefixString) &&
                !secret.getUsername().toLowerCase().contains(prefixString) &&
                !secret.getNote().toLowerCase().contains(prefixString))
              secrets.remove(i);
          } else {
            String description = secret.getDescription().toLowerCase();
            if (!description.startsWith(prefixString)) {
              secrets.remove(i);
            }
          }
        }

        results.values = secrets;
        results.count = secrets.size();
      } else {
        synchronized (allSecrets) {
          results.values = allSecrets;
          results.count = allSecrets.size();
        }
      }

      return results;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void publishResults(CharSequence prefix,
                                  FilterResults results) {
      secrets = (ArrayList<Secret>) results.values;
      notifyDataSetChanged();
      activity.setTitle();
    }
  }

  public Secret getSecret(int position) {
    return secrets.get(position);
  }

  public ArrayList<Secret> getAllSecrets() {
    return allSecrets;
  }

   public ArrayList<Secret> getAllAndDeletedSecrets() {
      if (deletedSecrets.size() == 0) {
         return allSecrets;
      }
      if (allSecrets.size() == 0) {
         return deletedSecrets;
      }
      ArrayList<Secret> allAndDeletedSecrets = new ArrayList<Secret>();
      synchronized (allSecrets) {
         int aIndex = 0, dIndex = 0;
         while (aIndex < allSecrets.size() || dIndex < deletedSecrets.size()) {
            if (aIndex == allSecrets.size()) {
               allAndDeletedSecrets.add(deletedSecrets.get(dIndex++));
            } else if (dIndex == deletedSecrets.size()) {
               allAndDeletedSecrets.add(allSecrets.get(aIndex++));
            } else {
               if (allSecrets.get(aIndex).compareTo(deletedSecrets.get(dIndex)) < 0) {
                  allAndDeletedSecrets.add(allSecrets.get(aIndex++));
               } else {
                  allAndDeletedSecrets.add(deletedSecrets.get(dIndex++));
               }
            }
         }
      }
      return allAndDeletedSecrets;
   }

  public Secret remove(int position) {
    Secret secret;
    synchronized (allSecrets) {
      secret = secrets.remove(position);
      if (secrets != allSecrets) {
        position = allSecrets.indexOf(secret);
        allSecrets.remove(position);
      }
    }

    return secret;
  }


  public Secret delete(int position) {
    int i;
    Secret secret;
    synchronized (allSecrets) {
      secret = remove(position);
      for (i = 0; i < deletedSecrets.size(); ++i) {
        Secret s = deletedSecrets.get(i);
        int compare = secret.compareTo(s);
        if (compare < 0) break;
        else if (compare == 0) {
           deletedSecrets.remove(i);
           break;
        }
      }
      deletedSecrets.add(i, secret);
      secret.setDeleted();
    }

    return secret;
  }

  public int insert(Secret secret) {
    int i;

    synchronized (allSecrets) {
      for (i = 0; i < allSecrets.size(); ++i) {
        Secret s = allSecrets.get(i);
        if (secret.compareTo(s) < 0)
          break;
      }
      allSecrets.add(i, secret);

      if (secrets != allSecrets) {
        for (i = 0; i < secrets.size(); ++i) {
          Secret s = secrets.get(i);
          if (secret.compareTo(s) < 0)
            break;
        }
        secrets.add(i, secret);
      }

      deletedSecrets.remove(secret);
    }

    if (!usernames.contains(secret.getUsername())) {
      usernames.add(secret.getUsername());
      usernameAdapter.add(secret.getUsername());
    }

    if (!emails.contains(secret.getEmail())) {
      emails.add(secret.getEmail());
      emailAdapter.add(secret.getEmail());
    }

    return i;
  }

  public void syncSecrets(ArrayList<Secret> changedSecrets) {
    if (changedSecrets != null) {
      synchronized (allSecrets) {
        OnlineAgentManager.syncSecrets(allSecrets, changedSecrets);
        deletedSecrets.clear();
      }
      notifyDataSetChanged();
    }
  }

  public ArrayAdapter<String> getUsernameAutoCompleteAdapter() {
    return usernameAdapter;
  }


  public ArrayAdapter<String> getEmailAutoCompleteAdapter() {
    return emailAdapter;
  }


  public String getFriendlyId(Secret secret) {
    String friendlyId = "";
    String username = secret.getUsername();
    Secret.LogEntry entry = secret.getMostRecentAccess();
    String lastAccessed = AccessLogActivity.getElapsedString(activity,
                                                              entry, 0);
    boolean hasUsername = null != username && username.length() > 0;
    if (hasUsername) {
      friendlyId = username;
    } else {
      String email = secret.getEmail();
      if (null != email && email.length() > 0)
        friendlyId = email;
    }

    if (friendlyId.length() > 0)
      friendlyId += ", ";

    friendlyId += lastAccessed;
    return friendlyId;
  }
}
