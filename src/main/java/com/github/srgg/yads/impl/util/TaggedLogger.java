/*
 * ⁣​
 * YADS
 * ⁣⁣
 * Copyright (C) 2015 - 2016 srgg
 * ⁣⁣
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ​⁣
 */
package com.github.srgg.yads.impl.util;

import org.slf4j.Logger;
import org.slf4j.Marker;

public class TaggedLogger implements Logger {
    final Logger logger;
    final String tag_;

    public TaggedLogger(Logger logger) {
        this(logger, "");
    }

    public TaggedLogger(Logger logger, String tag) {
        this.logger = logger;
        this.tag_ = tag;
    }

    protected String tag() {
        return tag_;
    }

    @Override
    public String getName() {
        return logger.getName();
    }

    @Override
    public boolean isTraceEnabled() {
        return logger.isTraceEnabled();
    }

    @Override
    public void trace(String msg) {
        logger.trace("{}{}", tag(), msg);
    }

    @Override
    public void trace(String format, Object arg) {
        logger.trace(tag() + format, arg);
    }

    @Override
    public void trace(String format, Object arg1, Object arg2) {
        logger.trace(tag() + format, arg1, arg2);
    }

    @Override
    public void trace(String format, Object... arguments) {
        logger.trace(tag() + format, arguments);
    }

    @Override
    public void trace(String msg, Throwable t) {
        logger.trace(tag() + msg, t);
    }

    @Override
    public boolean isTraceEnabled(Marker marker) {
        return logger.isTraceEnabled(marker);
    }

    @Override
    public void trace(Marker marker, String msg) {
        logger.trace(marker, tag() + msg);
    }

    @Override
    public void trace(Marker marker, String format, Object arg) {
        logger.trace(marker, tag() + format, arg);
    }

    @Override
    public void trace(Marker marker, String format, Object arg1, Object arg2) {
        logger.trace(marker, tag() + format, arg1, arg2);
    }

    @Override
    public void trace(Marker marker, String format, Object... argArray) {
        logger.trace(marker, tag() + format, argArray);
    }

    @Override
    public void trace(Marker marker, String msg, Throwable t) {
        logger.trace(marker, tag() + msg, t);
    }

    @Override
    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }

    @Override
    public void debug(String msg) {
        logger.debug(tag() + msg);
    }

    @Override
    public void debug(String format, Object arg) {
        logger.debug(tag() + format, arg);
    }

    @Override
    public void debug(String format, Object arg1, Object arg2) {
        logger.debug(tag() + format, arg1, arg2);
    }

    @Override
    public void debug(String format, Object... arguments) {
        logger.debug(tag() + format, arguments);
    }

    @Override
    public void debug(String msg, Throwable t) {
        logger.debug(tag() + msg, t);
    }

    @Override
    public boolean isDebugEnabled(Marker marker) {
        return logger.isDebugEnabled(marker);
    }

    @Override
    public void debug(Marker marker, String msg) {
        logger.debug(marker, tag() + msg);
    }

    @Override
    public void debug(Marker marker, String format, Object arg) {
        logger.debug(marker, tag() + format, arg);
    }

    @Override
    public void debug(Marker marker, String format, Object arg1, Object arg2) {
        logger.debug(marker, tag() + format, arg1, arg2);
    }

    @Override
    public void debug(Marker marker, String format, Object... arguments) {
        logger.debug(marker, tag() + format, arguments);
    }

    @Override
    public void debug(Marker marker, String msg, Throwable t) {
        logger.debug(marker, tag() + msg, t);
    }

    @Override
    public boolean isInfoEnabled() {
        return logger.isInfoEnabled();
    }

    @Override
    public void info(String msg) {
        logger.info(tag() + msg);
    }

    @Override
    public void info(String format, Object arg) {
        logger.info(tag() + format, arg);
    }

    @Override
    public void info(String format, Object arg1, Object arg2) {
        logger.info(tag() + format, arg1, arg2);
    }

    @Override
    public void info(String format, Object... arguments) {
        logger.info(tag() + format, arguments);
    }

    @Override
    public void info(String msg, Throwable t) {
        logger.info(tag() + msg, t);
    }

    @Override
    public boolean isInfoEnabled(Marker marker) {
        return logger.isInfoEnabled(marker);
    }

    @Override
    public void info(Marker marker, String msg) {
        logger.info(marker, tag() + msg);
    }

    @Override
    public void info(Marker marker, String format, Object arg) {
        logger.info(marker, tag() + format, arg);
    }

    @Override
    public void info(Marker marker, String format, Object arg1, Object arg2) {
        logger.info(marker, tag() + format, arg1, arg2);
    }

    @Override
    public void info(Marker marker, String format, Object... arguments) {
        logger.info(marker, tag() + format, arguments);
    }

    @Override
    public void info(Marker marker, String msg, Throwable t) {
        logger.info(marker, tag() + msg, t);
    }

    @Override
    public boolean isWarnEnabled() {
        return logger.isWarnEnabled();
    }

    @Override
    public void warn(String msg) {
        logger.warn(tag() + msg);
    }

    @Override
    public void warn(String format, Object arg) {
        logger.warn(tag() + format, arg);
    }

    @Override
    public void warn(String format, Object... arguments) {
        logger.warn(tag() + format, arguments);
    }

    @Override
    public void warn(String format, Object arg1, Object arg2) {
        logger.warn(tag() + format, arg1, arg2);
    }

    @Override
    public void warn(String msg, Throwable t) {
        logger.warn(tag() + msg, t);
    }

    @Override
    public boolean isWarnEnabled(Marker marker) {
        return logger.isWarnEnabled(marker);
    }

    @Override
    public void warn(Marker marker, String msg) {
        logger.warn(marker, tag() + msg);
    }

    @Override
    public void warn(Marker marker, String format, Object arg) {
        logger.warn(marker, tag() + format, arg);
    }

    @Override
    public void warn(Marker marker, String format, Object arg1, Object arg2) {
        logger.warn(marker, tag() + format, arg1, arg2);
    }

    @Override
    public void warn(Marker marker, String format, Object... arguments) {
        logger.warn(marker, tag() + format, arguments);
    }

    @Override
    public void warn(Marker marker, String msg, Throwable t) {
        logger.warn(marker, tag() + msg, t);
    }

    @Override
    public boolean isErrorEnabled() {
        return logger.isErrorEnabled();
    }

    @Override
    public void error(String msg) {
        logger.error(tag() + msg);
    }

    @Override
    public void error(String format, Object arg) {
        logger.error(tag() + format, arg);
    }

    @Override
    public void error(String format, Object arg1, Object arg2) {
        logger.error(tag() + format, arg1, arg2);
    }

    @Override
    public void error(String format, Object... arguments) {
        logger.error(tag() + format, arguments);
    }

    @Override
    public void error(String msg, Throwable t) {
        logger.error(tag() + msg, t);
    }

    @Override
    public boolean isErrorEnabled(Marker marker) {
        return logger.isErrorEnabled(marker);
    }

    @Override
    public void error(Marker marker, String msg) {
        logger.error(marker, tag() + msg);
    }

    @Override
    public void error(Marker marker, String format, Object arg) {
        logger.error(marker, tag() + format, arg);
    }

    @Override
    public void error(Marker marker, String format, Object arg1, Object arg2) {
        logger.error(marker, tag() + format, arg1, arg2);
    }

    @Override
    public void error(Marker marker, String format, Object... arguments) {
        logger.error(marker, tag() + format, arguments);
    }

    @Override
    public void error(Marker marker, String msg, Throwable t) {
        logger.error(marker, tag() + msg, t);
    }
}
