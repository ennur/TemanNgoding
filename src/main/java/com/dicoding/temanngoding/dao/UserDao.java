package com.dicoding.temanngoding.dao;

import com.dicoding.temanngoding.model.User;

import java.util.List;

public interface UserDao
{
    public List<User> get();
    public List<User> getByLineId(String aLineId);
    public int registerLineId(String aUserId, String aLineId, String aDisplayName);
};
