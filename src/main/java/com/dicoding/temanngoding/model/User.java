
package com.dicoding.temanngoding.model;

public class User
{
    public Long id;

    public String line_id;

    public String display_name;

    public User(Long aId, String aLineId, String aDisplayName)
    {
        id=aId;
        line_id=aLineId;
        display_name=aDisplayName;
    }

    public User()
    {
        
    }
};
