/*
 * Copyright 2010 Ronnie Kolehmainen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.cssxfire.webserver;

import com.github.cssxfire.CssXFireConnector;
import com.github.cssxfire.FirebugChangesBean;
import com.github.cssxfire.FirebugEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class HttpRequest implements Runnable {
  private static final Logger LOG = Logger.getInstance(HttpRequest.class.getName());

  private static final String EMPTY_STRING = "";
  private Socket socket;

  public HttpRequest(Socket socket) {
    this.socket = socket;
  }

  public void run() {
    try {
      processRequest();
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  private void processRequest() throws Exception {
    HttpResponse response;

    //Get references to sockets input and output streams
    InputStream is = this.socket.getInputStream();
    //Set up input stream filter
    try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
      //Get the request line of HTTP message
      String requestLine = StringUtil.trim(br.readLine());
      String req;
      while ((req = br.readLine()) != null) {
        req = req.trim().toLowerCase(Locale.US);
        if (req.isEmpty()) {
          break;
        }
      }
      if (StringUtil.isEmpty(requestLine)) return;

      String q = requestLine.toLowerCase(Locale.US);
      int six = q.indexOf(' ');
      int eix = q.indexOf(" http");
      if (eix != -1) {
        requestLine = requestLine.substring(six, eix).trim();
      }

      if (requestLine.startsWith("/files/")) {
        response = HttpResponse.createFileResponse(requestLine.substring("/files/".length()));
      }
      else {
        try {
          // Parse query params
          Map<String, String> params = getQueryMap(requestLine);

          // Extract the parameters
          String event = params.get("event");
          String property = params.get("property");
          String value = params.get("value");
          String selector = params.get("selector");
          String href = params.get("href");
          String media = params.get("media");
          boolean deleted = Boolean.parseBoolean(params.get("deleted"));
          boolean important = Boolean.parseBoolean(params.get("important"));

          // Notify application component
          if (property != null && value != null && selector != null) {
            final FirebugChangesBean changesBean = new FirebugChangesBean(media != null ? media : EMPTY_STRING,
                                                                          href != null ? href : EMPTY_STRING,
                                                                          selector, property, value, deleted, important);
            if (LOG.isDebugEnabled()) {
              LOG.debug("Got CSS property change: " + changesBean);
            }
            CssXFireConnector.getInstance().processCss(changesBean);
          }
          if (event != null) {
            if (LOG.isDebugEnabled()) {
              LOG.debug("Got event: " + event);
            }
            CssXFireConnector.getInstance().processEvent(new FirebugEvent(event));
          }

          response = HttpResponse.createEmptyOkResponse();
        }
        catch (MalformedQueryException e) {
          response = HttpResponse.createErrorResponse(e.getMessage());
        }
      }

      response.sendResponse(this.socket.getOutputStream());
    }
    finally {
      socket.close();
    }
  }

  private static Map<String, String> getQueryMap(String query) throws MalformedQueryException {
    Map<String, String> map = new HashMap<>();
    try {
      int i = query.indexOf('?');
      if (i == -1) {
        return map;
      }
      query = query.substring(i + 1);
      String[] params = query.split("&");
      for (String param : params) {
        int ix = param.indexOf('=');
        String name = param.substring(0, ix);
        name = URLDecoder.decode(name, "utf-8");
        String value = param.substring(ix + 1);
        value = URLDecoder.decode(value, "utf-8");
        map.put(name, value);
      }
      return map;
    }
    catch (Exception e) {
      throw new MalformedQueryException(query);
    }
  }

  public static class MalformedQueryException extends Exception {
    private MalformedQueryException(String message) {
      super(message);
    }

    @NotNull
    @Override
    public String toString() {
      return getClass().getSimpleName() + ": " + getMessage();
    }
  }
}
