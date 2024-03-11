/*
 * Copyright (c) 2015, Conveyal
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.conveyal.gtfs.model;

import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.error.GeneralError;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDate;
//feed
public class FeedInfo extends Entity  {

    private static final long serialVersionUID = 8718856987299076452L;
    protected String feedId = "NONE";
    protected String feedPublisherName;
    protected URL feedPublisherUrl;
    protected String feedLang;
    protected LocalDate feedStartDate;
     LocalDate feedEndDate;
    protected String feedVersion;

    public static FeedInfo copyFeedInfo(FeedInfo feedinfo) {
        FeedInfo feedinfocopy=new FeedInfo();
        feedinfocopy.feedEndDate =feedinfo.feedEndDate;
        feedinfocopy.feedId =feedinfo.feedId;
        feedinfocopy.feed=feedinfo.feed;
        feedinfocopy.feedLang =feedinfo.feedLang;
        feedinfocopy.feedVersion =feedinfo.feedVersion;
        feedinfocopy.feedPublisherName =feedinfo.feedPublisherName;
        feedinfocopy.feedPublisherUrl =feedinfo.feedPublisherUrl;
        feedinfocopy.feedStartDate =feedinfo.feedStartDate;
        feedinfocopy.sourceFileLine=feedinfo.sourceFileLine;
        return feedinfocopy;
    }




    public static class Loader extends Entity.Loader<FeedInfo> {

        public Loader(GTFSFeed feed) {
            super(feed, "feed_info");
        }

        @Override
        protected boolean isRequired() {
            return false;
        }

        @Override
        public void loadOneRow() throws IOException {
            FeedInfo fi = new FeedInfo();
            fi.sourceFileLine = row + 1; // offset line number by 1 to account for 0-based row index
            fi.feedId = getStringField("feed_id", false);
            fi.feedPublisherName = getStringField("feed_publisher_name", true);
            fi.feedPublisherUrl = getUrlField("feed_publisher_url", true);
            fi.feedLang = getStringField("feed_lang", true);
            fi.feedStartDate = getDateField("feed_start_date", false);
            fi.feedEndDate = getDateField("feed_end_date", false);
            fi.feedVersion = getStringField("feed_version", false);
            fi.feed = feed;
            if (feed.feedInfo.isEmpty()) {
                feed.feedInfo.put("NONE", fi);
                feed.setFeedId(fi.feedId);
            } else {
                feed.errors.add(new GeneralError(tableName, row, null, "FeedInfo contains more than one record."));
            }
        }

    }

}
