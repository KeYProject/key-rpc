/* This file is part of KeY - https://key-project.org
 * KeY is licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.keyproject.key.api;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.keyproject.key.api.remoteclient.ClientApi;

import static org.mockito.Mockito.mock;

/**
 * Test for reconnection handling: after a client disconnects, the API must
 * detach it (fall back to the no-op sink) rather than keep a dead remote proxy.
 */
class ClientDisconnectTest {
    @Test
    void disconnectClientDetachesTheClient() throws Exception {
        var api = new KeyApiImpl();
        var client = mock(ClientApi.class);
        api.setClientApi(client);

        Field field = KeyApiImpl.class.getDeclaredField("clientApi");
        field.setAccessible(true);
        Assertions.assertSame(client, field.get(api), "client should be attached");

        api.disconnectClient();

        Assertions.assertNotSame(client, field.get(api), "client should be detached");
        Assertions.assertNotNull(field.get(api), "should fall back to a no-op, not null");
    }
}
