package rest.uam;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

//import javax.enterprise.inject.Produces;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.Produces;


@Path("userdata")
public class MyResource {

    @Context
    private HttpServletRequest httpRequest;  

    @GET
    @Path("db")
    public String connectdb() throws Exception {
        try (Connection c = UamDb.connectDb()) {
            return (c != null) ? "connected" : "not connected";
        } catch (SQLException e) {
            e.printStackTrace();
            return "Database connection failed: " + e.getMessage();
        }
    }

    @POST
    @Path("register")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public String register(
            @FormParam("firstname") String firstname,
            @FormParam("lastname") String lastname,
            @FormParam("email") String email,
            @FormParam("password") String password,
            @FormParam("confirm-password") String confirmPassword) throws Exception {
        return UamDb.registerUser(firstname, lastname, email, password, confirmPassword);
    }

    @POST
    @Path("login")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response login(@FormParam("username") String username,
                          @FormParam("password") String password) throws Exception {
        String userType = UamDb.loginUser(username, password);
        if (userType.startsWith("error:")) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(userType).build();
        } else {
            HttpSession session = httpRequest.getSession(true);
            session.setAttribute("username", username);  // Store the username in session

            switch (userType) {
                case "admin":
                    return Response.ok("admin.html").build();
                case "manager":
                    return Response.ok("manager.html").build();
                default:
                    return Response.ok("user.html").build();
            }
        }
    }
    
    @POST
    @Path("add-resource")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response addResource(@FormParam("resource_name") String resourceName) throws Exception {
        if (resourceName == null || resourceName.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\":\"Resource Name is required\"}").build();
        }
        //400 error

        // Check if the resource already exists
        if (UamDb.resourceExists(resourceName)) {
            return Response.status(Response.Status.CONFLICT).entity("{\"error\":\"Resource already exists\"}").build();
        }
        //409 error

        String result = UamDb.addResource(resourceName);
        if (result.contains("error")) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(result).build();
            //500 error
        } else {
            return Response.ok(result).build();
        }
    }


    @GET
    @Path("resources/list")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getResources() throws Exception {
        try {
            String resourcesJson = UamDb.getResources();
            return Response.ok(resourcesJson).build();
        } catch (SQLException e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\":\"SQL error: " + e.getMessage() + "\"}").build();
        }
    }

    @POST
    @Path("remove-resource")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response removeResource(@FormParam("resource_name") String resourceName) throws Exception {
        String result = UamDb.removeResource(resourceName);
        if (result.contains("error")) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(result).build();
        } else {
            return Response.ok(result).build();
        }
    }
    
    @POST
    @Path("request-role-change")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Response requestRoleChange(@FormParam("role") String role) throws Exception {
        try {
            HttpSession session = httpRequest.getSession(false);
            if (session == null) {
                return Response.status(Response.Status.UNAUTHORIZED).entity("{\"error\":\"User not logged in\"}").build();
            }

            String username = (String) session.getAttribute("username");
            if (username == null) {
                return Response.status(Response.Status.UNAUTHORIZED).entity("{\"error\":\"Session expired\"}").build();
            }

            // Validate role
            if (!role.equals("manager") && !role.equals("admin")) {
                return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\":\"Invalid role\"}").build();
            }

            // Store the request in the database
            String result = UamDb.requestRoleChange(username, role);
            if (result.contains("error")) {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(result).build();
            } else {
                return Response.ok(result).build();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\":\"SQL error: " + e.getMessage() + "\"}").build();
        }
    }

    @GET
    @Path("current-role-request")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCurrentRoleRequest() throws Exception {
        HttpSession session = httpRequest.getSession(false);
		if (session == null) {
		    return Response.status(Response.Status.UNAUTHORIZED).entity("{\"error\":\"User not logged in\"}").build();
		}

		String username = (String) session.getAttribute("username");
		if (username == null) {
		    return Response.status(Response.Status.UNAUTHORIZED).entity("{\"error\":\"Session expired\"}").build();
		}

		String result = UamDb.getCurrentRoleRequest(username);
		return Response.ok(result).build();
    }

    @POST
    @Path("cancel-role-request")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Response cancelRoleRequest() throws Exception {
        try {
            HttpSession session = httpRequest.getSession(false);
            if (session == null) {
                return Response.status(Response.Status.UNAUTHORIZED).entity("{\"error\":\"User not logged in\"}").build();
            }

            String username = (String) session.getAttribute("username");
            if (username == null) {
                return Response.status(Response.Status.UNAUTHORIZED).entity("{\"error\":\"Session expired\"}").build();
            }

            String result = UamDb.cancelRoleChangeRequest(username);
            return Response.ok(result).build();
        } catch (SQLException e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\":\"SQL error: " + e.getMessage() + "\"}").build();
        }
    }

    @GET
    @Path("role-change-requests")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getRoleChangeRequests() throws Exception {
        try {
            String requestsJson = UamDb.getRoleChangeRequests();
            return Response.ok(requestsJson).build();
        } catch (SQLException e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\":\"SQL error: " + e.getMessage() + "\"}").build();
        }
    }
    @POST
    @Path("approve-request")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Response approveRequest(@FormParam("request_id") int requestId) throws Exception {
        try {
            String result = UamDb.approveRoleChangeRequest(requestId);
            if (result.contains("error")) {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(result).build();
            } else {
                return Response.ok(result).build();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\":\"SQL error: " + e.getMessage() + "\"}").build();
        }
    }


    @POST
    @Path("reject-request")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Response rejectRequest(@FormParam("request_id") int requestId) throws Exception {
        try {
            String result = UamDb.rejectRoleChangeRequest(requestId);
            if (result.contains("error")) {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(result).build();
            } else {
                return Response.ok(result).build();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\":\"SQL error: " + e.getMessage() + "\"}").build();
        }
    }
    @POST
    @Path("submit-resource-request")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response submitResourceRequest(@FormParam("resource") String resource) {
        HttpSession session = httpRequest.getSession(false);
        String username = (session != null) ? (String) session.getAttribute("username") : null;

        if (username == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("{\"error\":\"User not logged in\"}").build();
        }

        try {
            String result = UamDb.submitResourceRequest(username, resource);
            return Response.ok("{\"message\":\"" + result + "\"}").build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                           .entity("{\"error\":\"" + e.getMessage() + "\"}").build();
        }
    }

    @GET
    @Path("resource-requests")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getResourceRequests() {
        try {
            String requestsJson = UamDb.getPendingResourceRequests();
            return Response.ok(requestsJson).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\":\"" + e.getMessage() + "\"}").build();
        }
    }
    @POST
    @Path("approve-resource-request")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response approveResourceRequest(@FormParam("requestId") int requestId) {
        try {
            String result = UamDb.updateResourceRequestStatus(requestId, "Approved");
            return Response.ok(result).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\":\"" + e.getMessage() + "\"}").build();
        }
    }

    @POST
    @Path("reject-resource-request")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response rejectResourceRequest(@FormParam("requestId") int requestId) {
        try {
            String result = UamDb.updateResourceRequestStatus(requestId, "Rejected");
            return Response.ok(result).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\":\"" + e.getMessage() + "\"}").build();
        }
    }

    

    @POST
    @Path("get-approved-resources")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getApprovedResources() throws Exception {
        HttpSession session = httpRequest.getSession(false);
        if (session == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("{\"error\":\"User not logged in\"}")
                    .build();
        }

        String username = (String) session.getAttribute("username");
        if (username == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("{\"error\":\"Session expired\"}")
                    .build();
        }

        try {
            // Fetch approved resources for the current user from the database
            String resources = UamDb.getApprovedResources(username);

            // Return resources as a JSON array string
            return Response.ok(resources).build();
        } catch (SQLException e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\":\"SQL error: " + e.getMessage() + "\"}").build();
        }
    }

    @POST
    @Path("remove-resource-by-user")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Response removeResourceByUser(@FormParam("resource_name") String resourceName) throws Exception {
        try {
            HttpSession session = httpRequest.getSession(false);
            if (session == null) {
                return Response.status(Response.Status.UNAUTHORIZED).entity("{\"error\":\"User not logged in\"}").build();
            }

            String username = (String) session.getAttribute("username");
            if (username == null) {
                return Response.status(Response.Status.UNAUTHORIZED).entity("{\"error\":\"Session expired\"}").build();
            }

            
            boolean success = UamDb.removeResourceByUser(username, resourceName);
            if (success) {
                return Response.ok("{\"success\": true}").build();
            } else {
                return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\":\"Failed to remove resource\"}").build();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\":\"SQL error: " + e.getMessage() + "\"}").build();
        }
    }
    
    @GET
    @Path("get-all-usernames")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getAllUsernames() throws Exception {
        try {
            ResultSet rs = UamDb.getAllUsernames();
            StringBuilder usernames = new StringBuilder();

            while (rs.next()) {
                if (usernames.length() > 0) {
                    usernames.append('\n'); // Use newline as delimiter
                }
                usernames.append(rs.getString("user_name"));
            }

            rs.close();
            return Response.ok(usernames.toString()).build();
        } catch (SQLException e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("SQL error: " + e.getMessage()).build();
        }
    }
    
 
    @POST
    @Path("get-resources-for-user")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_PLAIN)
    public Response getResourcesForUser(@FormParam("username") String username) throws Exception {
        try {
            ResultSet rs = UamDb.getResourcesForUser(username);
            StringBuilder resources = new StringBuilder();

            while (rs.next()) {
                if (resources.length() > 0) {
                    resources.append('\n'); // Use newline as delimiter
                }
                resources.append(rs.getString("resource_name"));
            }

            rs.close();
            return Response.ok(resources.toString()).build();
        } catch (SQLException e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("SQL error: " + e.getMessage()).build();
        }
    }

    @POST
    @Path("remove-resource-for-user")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_PLAIN)
    public Response removeResourceForUser(@FormParam("username") String username,
                                           @FormParam("resource_name") String resourceName) throws Exception {
        try {
            boolean success = UamDb.removeResourceForUser(username, resourceName);
            if (success) {
                return Response.ok("success").build();
            } else {
                return Response.status(Response.Status.BAD_REQUEST).entity("Failed to remove resource").build();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("SQL error: " + e.getMessage()).build();
        }
    }
  
    @GET
    @Path("get-all-resources-user")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getAllResources() throws Exception {
        try {
            ResultSet rs = UamDb.getAllResourcesUser();
            StringBuilder resources = new StringBuilder();

            while (rs.next()) {
                if (resources.length() > 0) {
                    resources.append('\n'); // Use newline as delimiter
                }
                resources.append(rs.getString("resource_name"));
            }

            rs.close();
            return Response.ok(resources.toString()).build();
        } catch (SQLException e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("SQL error: " + e.getMessage()).build();
        }
    }
    @POST
    @Path("get-users-for-resource")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_PLAIN)
    public Response getUsersForResource(@FormParam("resource_name") String resourceName) throws Exception {
        try {
            ResultSet rs = UamDb.getUsersForResource(resourceName);
            StringBuilder users = new StringBuilder();

            while (rs.next()) {
                if (users.length() > 0) {
                    users.append('\n'); // Use newline as delimiter
                }
                users.append(rs.getString("username"));
            }

            rs.close();

            // Check if no users were found
            if (users.length() == 0) {
                return Response.ok("No users found for " + resourceName).build();
            } else {
                return Response.ok(users.toString()).build();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("SQL error: " + e.getMessage()).build();
        }
    }
    @GET
    @Path("get-all-usernames-res")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getAllUsernamess() throws Exception {
        try {
            ResultSet rs = UamDb.getAllUsernamess();
            StringBuilder usernames = new StringBuilder();

            while (rs.next()) {
                if (usernames.length() > 0) {
                    usernames.append('\n'); // Use newline as delimiter
                }
                usernames.append(rs.getString("user_name"));
            }

            rs.close();
            return Response.ok(usernames.toString()).build();
        } catch (SQLException e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("SQL error: " + e.getMessage()).build();
        }
    }
 
    @POST
    @Path("get-resources-for-username")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_PLAIN)
    public Response getResourcesForUsername(@FormParam("username") String username) throws Exception {
        try {
            ResultSet rs = UamDb.getResourcesForUsername(username);
            StringBuilder resources = new StringBuilder();

            while (rs.next()) {
                if (resources.length() > 0) {
                    resources.append('\n'); // Use newline as delimiter
                }
                resources.append(rs.getString("resource_name"));
            }

            rs.close();
            if (resources.length() == 0) {
                return Response.ok("No resources found for username").build();
            }
            return Response.ok(resources.toString()).build();
        } catch (SQLException e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("SQL error: " + e.getMessage()).build();
        }
    }
    @GET
    @Path("get-all-members")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getAllMembers() throws Exception {
        try {
            ResultSet rs = UamDb.getAllMembers();
            StringBuilder usernames = new StringBuilder();

            while (rs.next()) {
                if (usernames.length() > 0) {
                    usernames.append('\n'); // Use newline as delimiter
                }
                usernames.append(rs.getString("user_name"));
            }

            rs.close();
            return Response.ok(usernames.toString()).build();
        } catch (SQLException e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("SQL error: " + e.getMessage()).build();
        }
    }
 
    
    @GET
    @Path("get-manager-username")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getManagerUsername() {
        
        String managerUsername = "testManager";

        return Response.ok("{\"username\":\"" + managerUsername + "\"}").build();
    }
    
    @GET
    @Path("get-manager-for-user")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getManagerForUser() throws Exception {
     
		HttpSession session = httpRequest.getSession(false);
        if (session == null || session.getAttribute("username") == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("No session or username found").build();
        }

        String username = (String) session.getAttribute("username");

        try {
            String managerName = UamDb.getManagerForUser(username);
            if (managerName != null) {
                return Response.ok("{\"manager\":\"" + managerName + "\"}").build();
            } else {
                return Response.status(Response.Status.NOT_FOUND).entity("Manager not found").build();
            }
        } catch (SQLException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Database error: " + e.getMessage()).build();
        }
    }

    @POST
    @Path("assign-users")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Response assignUserToManager(@FormParam("username") String username,
                                        @Context HttpHeaders headers,
                                        @Context HttpServletRequest request) throws Exception {

        // Validate the 'username' parameter
        if (username == null || username.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity("{\"error\":\"Username parameter is missing\"}")
                           .build();
        }

        // Retrieve the session from the request
        HttpSession session = request.getSession(false); // Do not create a new session if one does not exist

      
        if (session == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                           .entity("{\"error\":\"Session is not available or has expired\"}")
                           .build();
        }

        // Retrieve the manager's username from the session
        String managerUsername = (String) session.getAttribute("username");

        // Check if the manager's username is null or empty
        if (managerUsername == null || managerUsername.trim().isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                           .entity("{\"error\":\"User not logged in or session has expired\"}")
                           .build();
        }

        // assign user to manager
        try {
            String result = UamDb.assignUserToManager(managerUsername, username);
            if (result.startsWith("error")) {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                               .entity("{\"error\":\"" + result + "\"}")
                               .build();
            } else {
                return Response.ok("{\"message\":\"" + result + "\"}").build();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                           .entity("{\"error\":\"SQL error: " + e.getMessage() + "\"}")
                           .build();
        }
    }
   
    @GET
    @Path("check-approvals")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getApprovalRequests() throws Exception {
        HttpSession session = httpRequest.getSession(false);
        if (session == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("User not logged in").build();
        }

        String username = (String) session.getAttribute("username");
        if (username == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("No username in session").build();
        }

        try {
            String approvalRequests = UamDb.getApprovalRequests(username);
            return Response.ok(approvalRequests).build();
        } catch (SQLException e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Database error").build();
        }
    }
    
    @POST
    @Path("/logout")
    public Response logout(@Context  HttpServletRequest request) {
       
		HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate(); // Invalidate the session
        }
        return Response.ok("Logged out successfully").build();
    }
  
    @GET
    @Path("fetchh-team-members")
    @Produces(MediaType.TEXT_PLAIN)
    public Response fetchhTeamMembers() throws Exception {
        HttpSession session = httpRequest.getSession(false);
        if (session == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("Not logged in").build();
        }

        String managerUsername = (String) session.getAttribute("username");
        if (managerUsername == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("No session found").build();
        }

        try {
            // Fetch the team members based on manager's username
            String teamMembers = UamDb.getTeamMembers(managerUsername);
            return Response.ok(teamMembers, MediaType.TEXT_PLAIN).build();
        } catch (SQLException e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Database error").build();
        }
    }
    @POST
    @Path("add-user")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Response addUser(
            @FormParam("first_name") String firstName,
            @FormParam("last_name") String lastName,
            @FormParam("email") String email) throws Exception {

        // Generate password default
        String password = firstName + lastName;
        String username;

        if (firstName == null || lastName == null || email == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity("{\"success\": false, \"error\": \"Missing parameters.\"}")
                           .build();
        }

        try {
            // Generate username
            username = UamDb.genUser(firstName, lastName);

            // Add the user to the database
            boolean success = UamDb.addUser(firstName, lastName, email, password, username);

            if (success) {
                return Response.ok("{\"success\": true}").build();
            } else {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                               .entity("{\"success\": false, \"error\": \"Failed to add user.\"}")
                               .build();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                           .entity("{\"success\": false, \"error\": \"Database error.\"}")
                           .build();
        }
    }
    @POST
    @Path("forgot-password")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response forgotPassword(
            @FormParam("username") String username,
            @FormParam("email") String email,
            @FormParam("newPassword") String newPassword,
            @FormParam("confirmPassword") String confirmPassword) {
        
        try {
            // Validate passwords match
            if (!newPassword.equals(confirmPassword)) {
                return Response.status(Response.Status.BAD_REQUEST).entity("error:Passwords do not match").build();
            }

            // Call the resetPassword method
            String result = UamDb.resetPassword(username, email, newPassword, confirmPassword);
            
            
            if (result.startsWith("success:")) {
                return Response.ok("Password reset successful. You can now login with your new password.").build();
            } else {
                return Response.status(Response.Status.BAD_REQUEST).entity(result).build();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("error:An error occurred while resetting password").build();
        }
    }
    @GET
    @Path("get-all-usernames-removeUser")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getAllUsernamesss() throws Exception {
        try {
            ResultSet rs = UamDb.getAllUsernamesss();
            StringBuilder usernames = new StringBuilder();

            while (rs.next()) {
                if (usernames.length() > 0) {
                    usernames.append('\n'); // Use newline as delimiter
                }
                usernames.append(rs.getString("user_name"));
            }

            rs.close();
            return Response.ok(usernames.toString()).build();
        } catch (SQLException e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("SQL error: " + e.getMessage()).build();
        }
    }
    @POST
    @Path("delete-user")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteUser(@FormParam("username") String username) throws Exception {
        try {
            int rowsAffected = UamDb.deleteUser(username);
            if (rowsAffected > 0) {
                return Response.ok("{\"success\":\"User removed successfully\"}").build();
            } else {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("{\"error\":\"User not found\"}").build();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\":\"SQL error: " + e.getMessage() + "\"}").build();
        }
    }
}
    
