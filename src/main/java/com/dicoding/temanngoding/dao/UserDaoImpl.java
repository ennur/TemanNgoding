
package com.dicoding.temanngoding.dao;

import com.dicoding.temanngoding.model.JoinEvent;
import com.dicoding.temanngoding.model.User;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Vector;

public class UserDaoImpl implements UserDao
{
    private final static String SQL_SELECT_ALL="SELECT id, user_id, line_id, display_name FROM user_table";
    private final static String SQL_GET_BY_LINE_ID=SQL_SELECT_ALL + " WHERE LOWER(user_id) LIKE LOWER(?);";
    private final static String SQL_REGISTER="INSERT INTO user_table (user_id, line_id, display_name) VALUES (?, ?, ?);";

    private final static String SQL_SELECT_ALL_EVENT="SELECT id, event_id, user_id, line_id, display_name FROM friend_table";
    private final static String SQL_JOIN_EVENT = "INSERT INTO friend_table (event_id, user_id, line_id, display_name) VALUES (?, ?, ?, ?);";
    private final static String SQL_GET_BY_EVENT_ID=SQL_SELECT_ALL_EVENT + " WHERE LOWER(event_id) LIKE LOWER(?);";
    private final static String SQL_GET_BY_JOIN=SQL_SELECT_ALL_EVENT + " WHERE LOWER(event_id) LIKE LOWER(?) AND LOWER(user_id) LIKE LOWER(?);";

    private JdbcTemplate mJdbc;

    private final static ResultSetExtractor<User> SINGLE_RS_EXTRACTOR=new ResultSetExtractor<User>()
    {
        @Override
        public User extractData(ResultSet aRs)
                throws SQLException, DataAccessException
        {
            while(aRs.next())
            {
                User p=new User(
                        aRs.getLong("id"),
                        aRs.getString("user_id"),
                        aRs.getString("line_id"),
                        aRs.getString("display_name"));

                return p;
            }
            return null;
        }
    };

    private final static ResultSetExtractor< List<User> > MULTIPLE_RS_EXTRACTOR=new ResultSetExtractor< List<User> >()
    {
        @Override
        public List<User> extractData(ResultSet aRs)
                throws SQLException, DataAccessException
        {
            List<User> list=new Vector<User>();
            while(aRs.next())
            {
                User p=new User(
                        aRs.getLong("id"),
                        aRs.getString("user_id"),
                        aRs.getString("line_id"),
                        aRs.getString("display_name"));
                list.add(p);
            }
            return list;
        }
    };

    private final static ResultSetExtractor<JoinEvent> SINGLE_RS_EXTRACTOR_EVENT=new ResultSetExtractor<JoinEvent>()
    {
        @Override
        public JoinEvent extractData(ResultSet aRs)
                throws SQLException, DataAccessException
        {
            while(aRs.next())
            {
                JoinEvent joinEvent = new JoinEvent(
                        aRs.getLong("id"),
                        aRs.getString("event_id"),
                        aRs.getString("user_id"),
                        aRs.getString("line_id"),
                        aRs.getString("display_name"));

                return joinEvent;
            }
            return null;
        }
    };

    private final static ResultSetExtractor< List<JoinEvent> > MULTIPLE_RS_EXTRACTOR_EVENT=new ResultSetExtractor< List<JoinEvent> >()
    {
        @Override
        public List<JoinEvent> extractData(ResultSet aRs)
                throws SQLException, DataAccessException
        {
            List<JoinEvent> list=new Vector<JoinEvent>();
            while(aRs.next())
            {
                JoinEvent joinEvent = new JoinEvent(
                        aRs.getLong("id"),
                        aRs.getString("event_id"),
                        aRs.getString("user_id"),
                        aRs.getString("line_id"),
                        aRs.getString("display_name"));
                list.add(joinEvent);
            }
            return list;
        }
    };

    public UserDaoImpl(DataSource aDataSource)
    {
        mJdbc=new JdbcTemplate(aDataSource);
    }

    public List<User> get()
    {
        return mJdbc.query(SQL_SELECT_ALL, MULTIPLE_RS_EXTRACTOR);
    }

    public List<User> getByUserId(String aUserId)
    {
        return mJdbc.query(SQL_GET_BY_LINE_ID, new Object[]{"%"+aUserId+"%"}, MULTIPLE_RS_EXTRACTOR);
    }

    public int registerLineId(String aUserId, String aLineId, String aDisplayName)
    {
        return mJdbc.update(SQL_REGISTER, new Object[]{aUserId, aLineId,  aDisplayName});
    }


    public int joinEvent(String aEventId, String aUserId, String aLineId, String aDisplayName)
    {
        return mJdbc.update(SQL_JOIN_EVENT, new Object[]{aEventId, aUserId,  aLineId, aDisplayName});
    }

    public List<JoinEvent> getEvent()
    {
        return mJdbc.query(SQL_SELECT_ALL_EVENT, MULTIPLE_RS_EXTRACTOR_EVENT);
    }

    public List<JoinEvent> getByEventId(String aEventId)
    {
        return mJdbc.query(SQL_GET_BY_EVENT_ID, new Object[]{"%"+aEventId+"%"}, MULTIPLE_RS_EXTRACTOR_EVENT);
    }
    public List<JoinEvent> getByJoin(String aEventId, String aUserId){
        return mJdbc.query(SQL_GET_BY_JOIN, new Object[]{"%"+aEventId+"%,%"+aUserId+"&"}, MULTIPLE_RS_EXTRACTOR_EVENT);
    }
};

