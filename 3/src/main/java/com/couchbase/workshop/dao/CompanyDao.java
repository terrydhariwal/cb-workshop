package com.couchbase.workshop.dao;

import com.couchbase.client.java.AsyncBucket;
import com.couchbase.client.java.document.JsonDocument;
import com.couchbase.client.java.document.json.JsonArray;
import com.couchbase.client.java.document.json.JsonObject;
import com.couchbase.workshop.pojo.Company;
import com.couchbase.workshop.conn.BucketFactory;
import com.couchbase.workshop.pojo.User;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import rx.Observable;

/**
 * The Data Access Object which wraps a Company object
 * 
 * @author David Maier <david.maier at couchbase.com>
 */
public class CompanyDao extends AJsonSerializable implements IAsyncDao {

    /**
     * Constants
     */
    public static final String TYPE = "company";
    public static final String PROP_TYPE = "type";
    public static final String PROP_ID = "id";
    public static final String PROP_NAME = "name";
    public static final String PROP_ADDRESS = "address";
    public static final String PROP_USERS = "users";
    
    /**
     * Logger
     */
    private static final Logger LOG = Logger.getLogger(CompanyDao.class.getName());
    
    /**
     * Bucket reference
     */
    private final AsyncBucket bucket =  BucketFactory.getAsyncBucket();
    
    /**
     * Company reference
     */
    private final Company company;

    /**
     * The constructor of the DAO
     * 
     * @param company 
     */
    public CompanyDao(Company company) {
        this.company = company;
    }

    @Override
    public Observable<Company> persist() {
           
        JsonDocument doc = toJson(this.company);
        
        //Update all users by using a bulk operation and then update the company
        //FYI: Optional error handling via try-catch for block #1
        if (company.getUsers().size() > 0)
        {
            Observable.from(company.getUsers()).flatMap(
        
                u -> {
                
                    UserDao userDAO = DAOFactory.createUserDao(u);
                    
                    return userDAO.persist();
                
                }
                
            ).last().toBlocking().single();
        }       
        
        return bucket
                .upsert(doc)
                .map((JsonDocument resultDoc) -> (Company) fromJson(resultDoc));

    }
    
    /**
     * 
     * ...
     * 
     *   .map(
     *                
     *                 new Func1<Company, Company>(){
     *                
     *                      public Company call(Company c) {
     *               
     *                          for (User user : c.getUsers()) {
     *                              
     *                              UserDao userDAO = DAOFactory.createUserDao(user);
     *                              userDAO.get().subscribe(
     *                              
     *                                      ( u -> {user.setFirstName(u.getFirstName());user.setLastName(u.getLastName()); user.setEmail(u.getEmail());user.setBirthDay(u.getBirthDay()); } )
     *                              );
     *                          }
     *                         
     *                          return c;
     *                       }
     *                 }
     *         
     *           );
     * @return 
     */
    @Override
    public Observable<Company> get() {
        
        //Get the company by the id
        String id = TYPE + "::" + company.getId();
        
        return bucket.get(id)
              .map((JsonDocument resultDoc) -> (Company) fromJson(resultDoc) )
              .map(
                      (Company c) -> {
                            
                            //TODO: Do a bulk get
                            //for each
                            c.getUsers().stream().forEach((user) -> {
                            
                                UserDao userDAO = DAOFactory.createUserDao(user);
                     
                                userDAO.get().subscribe(
                                        
                                        //Set the properties of the users those are attached to the company
                                        u -> {  user.setFirstName(u.getFirstName());
                                                user.setLastName(u.getLastName()); 
                                                user.setEmail(u.getEmail());
                                                user.setBirthDay(u.getBirthDay()); 
                                            } 
                                );
                            });
                          
                        return c;
                    }
              );
    }
        

    @Override
    protected JsonDocument toJson(Object obj) {
        
        Company tmpComp = (Company) obj;
        
        //Create an empty JSON document
        JsonObject json = JsonObject.empty();
 
        json.put(PROP_TYPE, TYPE);
        if (tmpComp.getId() != null) json.put(PROP_ID, tmpComp.getId());
        if (tmpComp.getName() != null ) json.put(PROP_NAME, tmpComp.getName());
        if (tmpComp.getAddress() != null ) json.put(PROP_ADDRESS, tmpComp.getAddress());
           
        List<User> users = tmpComp.getUsers();
        
        JsonArray userArray = JsonArray.create();
        
        for (User user : users) {
        
           userArray.add(UserDao.TYPE + "::" + user.getUid());
        }
        
        json.put(PROP_USERS, userArray);
        
        JsonDocument doc = JsonDocument.create(TYPE + "::" + tmpComp.getId(), json);
        
        return doc;
    }

    @Override
    protected Object fromJson(JsonDocument doc) {
     
        JsonObject json = doc.content();
        
        Company tmpComp = new Company(json.getString(PROP_ID));
       
        tmpComp.setName(json.getString(PROP_NAME));
        tmpComp.setAddress(json.getString(PROP_ADDRESS));
        
        List<User> users = new ArrayList<>();
        JsonArray userArr = json.getArray(PROP_USERS);
        
        for (int i = 0; i < userArr.size(); i++) {

            String userKey = userArr.getString(i);
            String uid  = userKey.split(UserDao.TYPE + "::")[1];
            
            //Don't fill the users yet
            users.add(new User(uid));
        }
        
        tmpComp.setUsers(users);
        
        return tmpComp;
    }
  
}