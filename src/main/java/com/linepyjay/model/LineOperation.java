package com.linepyjay.model;

import java.util.Map;

/**
 * Lightweight representation of a LINE Operation received from the poll endpoint.
 * Mirrors the Thrift Operation / Message structure used in main.py.
 */
public class LineOperation {
    private int type;
    private long revision;
    private LineMessage message;

    public int getType()              { return type; }
    public void setType(int type)     { this.type = type; }

    public long getRevision()                    { return revision; }
    public void setRevision(long revision)       { this.revision = revision; }

    public LineMessage getMessage()              { return message; }
    public void setMessage(LineMessage message)  { this.message = message; }

    // ── Inner: LineMessage ────────────────────────────────────────────────────
    public static class LineMessage {
        private String id;
        private String to;
        private String from;          // _from in Python
        private String text;
        private int    contentType;
        private Map<String, String> contentMetadata;

        public String getId()                             { return id; }
        public void setId(String id)                     { this.id = id; }

        public String getTo()                            { return to; }
        public void setTo(String to)                     { this.to = to; }

        public String getFrom()                          { return from; }
        public void setFrom(String from)                 { this.from = from; }

        public String getText()                          { return text; }
        public void setText(String text)                 { this.text = text; }

        public int getContentType()                             { return contentType; }
        public void setContentType(int contentType)            { this.contentType = contentType; }

        public Map<String, String> getContentMetadata()             { return contentMetadata; }
        public void setContentMetadata(Map<String, String> md)      { this.contentMetadata = md; }
    }
}
