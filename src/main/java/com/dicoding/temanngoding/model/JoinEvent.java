
package com.dicoding.temanngoding.model;

public class JoinEvent
{
    public Long id;

    public String event_id;

    public String line_id;

    public JoinEvent(Long aId, String aEventId, String aLineId)
    {
        id = aId;
        event_id = aEventId;
        line_id = aLineId;
    }

    public JoinEvent()
    {
        
    }
};
