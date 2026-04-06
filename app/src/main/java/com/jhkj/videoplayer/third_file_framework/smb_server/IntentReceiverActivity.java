/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package com.jhkj.videoplayer.third_file_framework.smb_server;
import static com.jhkj.videoplayer.third_file_framework.smb_server.Intents.ACTION_START;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.jhkj.videoplayer.third_file_framework.smb_server.service.SmbService;


public class IntentReceiverActivity extends Activity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();
        final String action = intent.getAction();

        if (ACTION_START.equals(action)) {
            SmbService.startService(this, false);
        }

        finish();
    }
}
