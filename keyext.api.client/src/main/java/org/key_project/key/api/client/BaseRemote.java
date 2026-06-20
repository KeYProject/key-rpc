/* This file is part of KeY - https://key-project.org
 * KeY is licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.key.api.client;


import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.SneakyThrows;

import java.lang.reflect.Type;

/**
 * @author Alexander Weigl
 * @version 1 (22.11.24)
 */
public class BaseRemote {
    private final RPCLayer layer;
    private final Gson gson = createGson();

    public BaseRemote(RPCLayer rpcLayer) {
        this.layer = rpcLayer;
    }

    public <T> T _call_sync(Type clazz, String s, Object... params) {
        var json = layer.callSync(s, params);
        return fromJson(json, clazz);
    }

    @SneakyThrows
    public void _call_async(String s, Object... params) {
        layer.callAsync(s, params);
    }

    protected Gson createGson() {
        Gson gson = new Gson();
        return gson;
    }

    protected <T> T fromJson(JsonObject json, Type clazz) {
        return gson.fromJson(json, clazz);
    }
}
