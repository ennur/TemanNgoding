package com.dicoding.temanngoding.dao;

import com.dicoding.temanngoding.model.JoinEvent;
import com.dicoding.temanngoding.model.User;

import java.util.List;

public interface UserDao
{
    public List<User> get();
    public List<User> getByLineId(String aLineId);
    public int registerLineId(String aLineId, String aDisplayName);
    public int joinEvent(String aEventId, String aLineId);
    public List<JoinEvent> getEvent();
    public List<JoinEvent> getByEventId(String aLineId);
};
