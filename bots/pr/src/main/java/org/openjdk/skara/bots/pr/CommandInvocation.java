/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.openjdk.skara.bots.pr;

import java.time.ZonedDateTime;
import org.openjdk.skara.host.HostUser;

import java.util.Optional;

class CommandInvocation {
    private final String id;
    private final HostUser user;
    private final CommandHandler handler;
    private final String name;
    private final String args;
    private final ZonedDateTime createdAt;

    CommandInvocation(String id, HostUser user, CommandHandler handler, String name, String args, ZonedDateTime createdAt) {
        this.id = id;
        this.user = user;
        this.handler = handler;
        this.name = name;
        this.args = args != null ? args.strip() : "";
        this.createdAt = createdAt;
    }

    String id() {
        return id;
    }

    HostUser user() {
        return user;
    }

    Optional<CommandHandler> handler() {
        return Optional.ofNullable(handler);
    }

    String name() {
        return name;
    }

    String args() {
        return args;
    }

    ZonedDateTime createdAt() {
        return createdAt;
    }
}
