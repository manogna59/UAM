package rest.uam;


import java.io.File;
import java.io.FileNotFoundException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Pattern;

import javax.servlet.http.HttpSession;



public class UamDb {

    // Connect to the database
    public static Connection connectDb() throws Exception {
        return Mydb.connect();
    }

    public static String registerUser(String firstname, String lastname, String email, String password, String confirmPassword) throws Exception {
        // Password Constraints
        int minPasswordLength = 8;
        int maxPasswordLength = 20;
        String passwordPattern = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@#$%^&+=]).{" + minPasswordLength + "," + maxPasswordLength + "}$";

        // Validate passwords match
        if (!password.equals(confirmPassword)) {
            return "error:Passwords do not match";
        }

        // Validate password constraints
        if (!isPasswordValid(password, passwordPattern, minPasswordLength, maxPasswordLength)) {
            return "error:Password must be between " + minPasswordLength + " and " + maxPasswordLength + " characters long, include at least one uppercase letter, one lowercase letter, one digit, and one special character.";
        }

        String username = genUser(firstname, lastname);
        String user_type = "user";
        String sql = "INSERT INTO userdata (first_name, last_name, email_address, user_type, h_password, join_date, user_name) VALUES (?, ?, ?, ?, ?, CURDATE(), ?)";

        String checkQuery = "SELECT COUNT(*) FROM userdata";

        try (Connection conn = connectDb()) {
            if (conn == null) {
                return "error:Database connection failed";
            }

            try (PreparedStatement checkStmt = conn.prepareStatement(checkQuery);
                 ResultSet rs1 = checkStmt.executeQuery()) {
                if (rs1.next() && rs1.getInt(1) == 0) {
                    user_type = "admin";
                }
            }

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                String encryptedPassword = encryptPassword(password);

                pstmt.setString(1, firstname);
                pstmt.setString(2, lastname);
                pstmt.setString(3, email);
                pstmt.setString(4, user_type);
                pstmt.setString(5, encryptedPassword);
                pstmt.setString(6, username);

                int rowsAffected = pstmt.executeUpdate();
                return (rowsAffected > 0) ? "success:" + username : "error:Registration failed";
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "error:SQL error: " + e.getMessage();
        }
    }

    private static boolean isPasswordValid(String password, String pattern, int minLength, int maxLength) {
        if (password.length() < minLength || password.length() > maxLength) {
            return false;
        }

        
        Pattern regexPattern = Pattern.compile(pattern);
        return regexPattern.matcher(password).matches();
    }


    public static String genUser(String firstname, String lastname) throws Exception {
        String basename = firstname + "." + lastname;
        String username = basename;

        try (Connection conn = connectDb()) {
            String query = "SELECT COUNT(*) FROM userdata WHERE user_name LIKE ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(query)) {
                checkStmt.setString(1, username + "%");

                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) {
                        int count = rs.getInt(1);
                        if (count > 0) {
                            username = basename + (count);
                        }
                    }
                }
            }
        }
        return username;
    }

    public static String loginUser(String username, String password) throws Exception {
        String encryptedPassword = encryptPassword(password);
        String sql = "SELECT user_type FROM userdata WHERE user_name = ? AND h_password = ?";

        try (Connection conn = connectDb()) {
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, username);
                pstmt.setString(2, encryptedPassword);

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("user_type");
                    } else {
                        return "error:Invalid username or password";
                    }
                }
            }
        }
    }

    public static String addResource(String resourceName) throws Exception {
//        if (resourceName == null || resourceName.trim().isEmpty()) {
//            return "{\"error\":\"Resource Name is required\"}";
//        }

        String sql = "INSERT INTO resource (resource_name) VALUES (?)";

        try (Connection conn = connectDb(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, resourceName);
            int rowsAffected = pstmt.executeUpdate();
            return (rowsAffected > 0) ? "{\"success\":\"Resource added successfully\"}" : "{\"error\":\"Failed to add resource\"}";
        }
    }
    public static boolean resourceExists(String resourceName) throws Exception {
        String sql = "SELECT COUNT(*) FROM resource WHERE resource_name = ?";
        
        try (Connection conn = connectDb(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, resourceName);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    public static String getResources() throws Exception {
        String sql = "SELECT resource_name FROM resource";
        StringBuilder resourcesJson = new StringBuilder("{\"resources\":[");
        
        try (Connection conn = connectDb(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            try (ResultSet rs = pstmt.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) {
                        resourcesJson.append(",");
                    }
                    resourcesJson.append("\"").append(rs.getString("resource_name")).append("\"");
                    first = false;
                }
                resourcesJson.append("]}");
                return resourcesJson.toString();
            }
        }
    }

    public static String removeResource(String resourceName) throws Exception {
        if (resourceName == null || resourceName.trim().isEmpty()) {
            return "{\"error\":\"Resource Name is required\"}";
        }

        String deleteResourceRequestsSql = "DELETE FROM resource_requests WHERE resource_name = ?";
        String deleteResourceSql = "DELETE FROM resource WHERE resource_name = ?";

        
        try (Connection conn = connectDb()) {
            conn.setAutoCommit(false); // Start transaction

            try (PreparedStatement pstmt1 = conn.prepareStatement(deleteResourceRequestsSql);
                 PreparedStatement pstmt2 = conn.prepareStatement(deleteResourceSql)) {

                // Delete from resource_requests
                pstmt1.setString(1, resourceName);
                pstmt1.executeUpdate();

                // Delete from resource
                pstmt2.setString(1, resourceName);
                int rowsAffected = pstmt2.executeUpdate();

                if (rowsAffected > 0) {
                    conn.commit(); // Commit transaction
                    return "{\"success\":\"Resource removed successfully\"}";
                } else {
                    conn.rollback(); // Rollback transaction on failure
                    return "{\"error\":\"Failed to remove resource\"}";
                }
            } catch (SQLException e) {
                conn.rollback(); // Rollback transaction on exception
                throw new SQLException("Error removing resource: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new Exception("Database connection error: " + e.getMessage(), e);
        }
    }

    
    public static String requestRoleChange(String username, String role) throws Exception {
        if (username == null || role == null) {
            return "{\"error\":\"Invalid input\"}";
        }

        // Check for existing pending request
        String checkSql = "SELECT * FROM requestss WHERE request_by = ? AND approval_status = 'Pending'";
        try (Connection conn = connectDb(); PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
            checkStmt.setString(1, username);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next()) {
                    return "{\"error\":\"You have an existing pending request. Please cancel it before requesting a new role.\"}";
                }
            }
        }

        // Insert new role change request
        String insertSql = "INSERT INTO requestss (request_by, request_for, approval_status) VALUES (?, ?, 'Pending')";
        try (Connection conn = connectDb(); PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
            insertStmt.setString(1, username);
            insertStmt.setString(2, role);
            int rowsAffected = insertStmt.executeUpdate();
            return (rowsAffected > 0) ? "{\"success\":\"Request submitted successfully\"}" : "{\"error\":\"Failed to submit request\"}";
        }
    }

   
 // Method to get role change requests
    public static String getRoleChangeRequests() throws Exception {
        String sql = "SELECT id, request_by, request_for, approval_status FROM requestss WHERE approval_status = 'Pending'";
        StringBuilder requestsJson = new StringBuilder("{\"requests\":[");
        
        try (Connection conn = connectDb(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            try (ResultSet rs = pstmt.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) {
                        requestsJson.append(",");
                    }
                    requestsJson.append("{\"id\":").append(rs.getInt("id"))
                                .append(",\"username\":\"").append(rs.getString("request_by"))
                                .append("\",\"role\":\"").append(rs.getString("request_for"))
                                .append("\",\"status\":\"").append(rs.getString("approval_status")).append("\"}");
                    first = false;
                }
                requestsJson.append("]}");
                return requestsJson.toString();
            }
        }
    }


    // Method to cancel a role change request
    public static String cancelRoleChangeRequest(String username) throws Exception {
        String sql = "DELETE FROM requestss WHERE request_by = ? AND approval_status = 'Pending'";
        try (Connection conn = connectDb(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                return "{\"success\":\"Request cancelled successfully\"}";
            } else {
                return "{\"error\":\"Request not found\"}";
            }
        }
    }
    public static String getCurrentRoleRequest(String username) throws Exception {
        String sql = "SELECT request_for FROM requestss WHERE request_by = ? AND approval_status = 'Pending'";
        try (Connection conn = connectDb(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return "{\"currentRequest\":{\"role\":\"" + rs.getString("request_for") + "\"}}";
                } else {
                    return "{\"currentRequest\":null}";
                }
            }
        }
    }
    public static String approveRoleChangeRequest(int requestId) throws Exception {
        Connection conn = null;
        PreparedStatement pstmt = null;
        PreparedStatement updateUserPstmt = null;
        PreparedStatement findNewManagerPstmt = null;
        PreparedStatement reassignTeamPstmt = null;
        ResultSet rs = null;
        String username = null;
        String newManager = null;

        try {
            conn = connectDb();
            conn.setAutoCommit(false); // Start transaction

            // Get the role and username from the request
            String getRequestSql = "SELECT request_for, request_by FROM requestss WHERE id = ?";
            pstmt = conn.prepareStatement(getRequestSql);
            pstmt.setInt(1, requestId);
            rs = pstmt.executeQuery();

            if (rs.next()) {
                String role = rs.getString("request_for");
                username = rs.getString("request_by");

                // Check if the request is for admin role
                if ("admin".equals(role)) {
                    // Approve the request
                    String updateRequestSql = "UPDATE requestss SET approval_status = 'Approved' WHERE id = ?";
                    pstmt = conn.prepareStatement(updateRequestSql);
                    pstmt.setInt(1, requestId);
                    pstmt.executeUpdate();

                    // Check if the user is a manager for other users
                    String checkManagerSql = "SELECT COUNT(*) FROM userdata WHERE manager_name = ?";
                    pstmt = conn.prepareStatement(checkManagerSql);
                    pstmt.setString(1, username);
                    rs = pstmt.executeQuery();

                    if (rs.next() && rs.getInt(1) > 0) {
                        // Update the requester's user type to 'admin' and manager_name to NULL
                        String updateUserSql = "UPDATE userdata SET user_type = 'admin', manager_name = NULL WHERE user_name = ?";
                        updateUserPstmt = conn.prepareStatement(updateUserSql);
                        updateUserPstmt.setString(1, username);
                        updateUserPstmt.executeUpdate();

                        // Find the new manager who joined first (earliest join date)
                        String findNewManagerSql = "SELECT user_name FROM userdata WHERE manager_name = ? ORDER BY join_date ASC LIMIT 1";
                        findNewManagerPstmt = conn.prepareStatement(findNewManagerSql);
                        findNewManagerPstmt.setString(1, username);
                        rs = findNewManagerPstmt.executeQuery();

                        if (rs.next()) {
                            newManager = rs.getString("user_name");

                            // Update the new manager's user type to 'manager' and set manager_name to NULL
                            String updateNewManagerSql = "UPDATE userdata SET user_type = 'manager', manager_name = NULL WHERE user_name = ?";
                            updateUserPstmt = conn.prepareStatement(updateNewManagerSql);
                            updateUserPstmt.setString(1, newManager);
                            updateUserPstmt.executeUpdate();

                            // Reassign team members to the new manager
                            String reassignTeamSql = "UPDATE userdata SET manager_name = ? WHERE manager_name = ? AND user_name <> ?";
                            reassignTeamPstmt = conn.prepareStatement(reassignTeamSql);
                            reassignTeamPstmt.setString(1, newManager);
                            reassignTeamPstmt.setString(2, username);
                            reassignTeamPstmt.setString(3, newManager);
                            reassignTeamPstmt.executeUpdate();
                        } else {
                            throw new Exception("No team members found for reassignment.");
                        }
                    } else {
                        // If the user is not a manager, just update the role
                        String updateUserSql = "UPDATE userdata SET user_type = 'admin' WHERE user_name = ?";
                        updateUserPstmt = conn.prepareStatement(updateUserSql);
                        updateUserPstmt.setString(1, username);
                        updateUserPstmt.executeUpdate();
                    }

                    conn.commit(); // Commit transaction
                    return "{\"success\":\"Request approved, user role updated, and team reassigned successfully\"}";

                } else {
                    // Handle other role changes if needed
                    String updateRequestSql = "UPDATE requestss SET approval_status = 'Approved' WHERE id = ?";
                    pstmt = conn.prepareStatement(updateRequestSql);
                    pstmt.setInt(1, requestId);
                    pstmt.executeUpdate();
                    
                    String updateUserSql = "UPDATE userdata SET user_type = ? WHERE user_name = ?";
                    pstmt = conn.prepareStatement(updateUserSql);
                    pstmt.setString(1, role);
                    pstmt.setString(2, username);
                    pstmt.executeUpdate();
                    
                    conn.commit(); // Commit transaction
                    return "{\"success\":\"Request approved and user role updated successfully\"}";
                }

            } else {
                return "{\"error\":\"Request not found\"}";
            }

        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    rollbackEx.printStackTrace();
                }
            }
            e.printStackTrace();
            return "{\"error\":\"Error: " + e.getMessage() + "\"}";
        } finally {
            if (rs != null) rs.close();
            if (pstmt != null) pstmt.close();
            if (updateUserPstmt != null) updateUserPstmt.close();
            if (findNewManagerPstmt != null) findNewManagerPstmt.close();
            if (reassignTeamPstmt != null) reassignTeamPstmt.close();
            if (conn != null) conn.close();
        }
    }

    // Method to reject a role change request
    public static String rejectRoleChangeRequest(int requestId) throws Exception {
        String sql = "UPDATE requestss SET approval_status = 'Rejected' WHERE id = ?";
        try (Connection conn = connectDb(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, requestId);
            int rowsAffected = pstmt.executeUpdate();
            return (rowsAffected > 0) ? "{\"success\":\"Request rejected successfully\"}" : "{\"error\":\"Request not found\"}";
        }
    }




    public static String submitResourceRequest(String username, String resourceName) throws Exception {
        // SQL to check the status of the latest request
        String checkStatusSql = "SELECT approval_status FROM resource_requests WHERE username = ? AND resource_name = ? ORDER BY id DESC LIMIT 1";
        
        // SQL to insert a new request
        String insertSql = "INSERT INTO resource_requests (username, resource_name, approval_status) VALUES (?, ?, 'Pending')";

        try (Connection conn = connectDb();
             PreparedStatement checkStmt = conn.prepareStatement(checkStatusSql);
             PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {

            // Check the current status of the latest request for the same resource by the same user
            checkStmt.setString(1, username);
            checkStmt.setString(2, resourceName);
            ResultSet rs = checkStmt.executeQuery();

            if (rs.next()) {
                String status = rs.getString("approval_status");
                System.out.println("Current status: " + status); // Debugging line
                if ("Approved".equals(status) || "Pending".equals(status)) {
                    return "Request cannot be submitted. Resource is already " + status;
                }
            }

            // Insert the new request if no pending or approved request exists
            insertStmt.setString(1, username);
            insertStmt.setString(2, resourceName);
            insertStmt.executeUpdate();
            return "Resource request submitted successfully";
        } catch (SQLException e) {
            e.printStackTrace();
            throw new Exception("SQL error: " + e.getMessage());
        }
    }

    public static String updateResourceRequestStatus(int requestId, String status) throws Exception {
        if (!status.equals("Approved") && !status.equals("Rejected")) {
            return "Invalid status";
        }

        String sql = "UPDATE resource_requests SET approval_status = ? WHERE id = ?";

        try (Connection conn = connectDb();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, status);
            pstmt.setInt(2, requestId);

            int rowsAffected = pstmt.executeUpdate();
            return (rowsAffected > 0) ? status + " resource request successfully" : "Failed to update resource request";
        } catch (SQLException e) {
            e.printStackTrace();
            throw new Exception("SQL error: " + e.getMessage());
        }
    }

 

    public static String getPendingResourceRequests() throws Exception {
        String sql = "SELECT id, username, resource_name FROM resource_requests WHERE approval_status = 'Pending'";
        StringBuilder requestsJson = new StringBuilder("{\"resource-requests\":[");

        try (Connection conn = connectDb();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            boolean first = true;
            while (rs.next()) {
                if (!first) {
                    requestsJson.append(",");
                }
                requestsJson.append("{")
                        .append("\"id\":").append(rs.getInt("id")).append(",")
                        .append("\"username\":\"").append(rs.getString("username")).append("\",")
                        .append("\"resourceName\":\"").append(rs.getString("resource_name")).append("\"")
                        .append("}");
                first = false;
            }
            requestsJson.append("]}");
        } catch (SQLException e) {
            e.printStackTrace();
            throw new Exception("SQL error: " + e.getMessage());
        }

        return requestsJson.toString();
    }
    public static String getApprovedResources(String username) throws Exception {
        StringBuilder result = new StringBuilder("[");
        String sql = "SELECT DISTINCT resource_name FROM resource_requests WHERE username = ? AND approval_status = 'Approved'";

        try (Connection conn = connectDb();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) {
                        result.append(",");
                    }
                    result.append("{\"resource\":\"").append(rs.getString("resource_name")).append("\"}");
                    first = false;
                }
            }
        }

        result.append("]");
        return result.toString();
    }


   
   
    public static boolean removeResourceByUser(String username, String resourceName) throws Exception {
        String sql = "DELETE FROM resource_requests WHERE username = ? AND resource_name = ? AND approval_status = 'Approved'";
        try (Connection conn = connectDb();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, username);
            pstmt.setString(2, resourceName);
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
        }
    }
    public static ResultSet getAllUsernames() throws Exception {
        String sql = "SELECT user_name FROM userdata WHERE user_type != 'admin'";
; 
        Connection conn = connectDb();
        PreparedStatement pstmt = conn.prepareStatement(sql);
        return pstmt.executeQuery();
    }
    public static ResultSet getAllUsernamess() throws Exception {
        String sql = "SELECT user_name FROM userdata WHERE user_type != 'admin'";; 
        Connection conn = connectDb();
        PreparedStatement pstmt = conn.prepareStatement(sql);
        return pstmt.executeQuery();
    }
    public static ResultSet getAllUsernamesss() throws Exception {
        String sql = "SELECT user_name FROM userdata WHERE user_type != 'admin'";; 
        Connection conn = connectDb();
        PreparedStatement pstmt = conn.prepareStatement(sql);
        return pstmt.executeQuery();
    }
    
    public static ResultSet getResourcesForUser(String username) throws Exception {
        String sql = "SELECT resource_name FROM resource_requests WHERE username = ? AND approval_status = 'Approved'";
        Connection conn = connectDb();
        PreparedStatement pstmt = conn.prepareStatement(sql);
        pstmt.setString(1, username);
        return pstmt.executeQuery();
    }


    public static boolean removeResourceForUser(String username, String resourceName) throws Exception {
        String sql = "DELETE FROM resource_requests WHERE username = ? AND resource_name = ? AND approval_status = 'Approved'";
        try (Connection conn = connectDb();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, resourceName);
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
        }
    }
    public static ResultSet getAllResourcesUser() throws Exception {
        String sql = "SELECT resource_name FROM resource";
        Connection conn = connectDb();
        PreparedStatement pstmt = conn.prepareStatement(sql);
        return pstmt.executeQuery();
    }


    // Method to get users for a specific resource
    public static ResultSet getUsersForResource(String resourceName) throws Exception {
        String sql = "SELECT username FROM resource_requests WHERE resource_name = ? AND approval_status = 'Approved'";
        Connection conn = connectDb();
        PreparedStatement pstmt = conn.prepareStatement(sql);
        pstmt.setString(1, resourceName);
        return pstmt.executeQuery();
    }
    public static ResultSet getAllMembers() throws Exception {
        String sql ="SELECT user_name FROM userdata WHERE user_type = 'user' AND manager_name IS NULL";

        Connection conn = connectDb();
        PreparedStatement pstmt = conn.prepareStatement(sql);
        return pstmt.executeQuery();
    }
    public static ResultSet getResourcesForUsername(String username) throws Exception {
        String sql = "SELECT resource_name FROM resource_requests WHERE username = ? AND approval_status = 'Approved'";
        Connection conn = connectDb();
        PreparedStatement pstmt = conn.prepareStatement(sql);
        pstmt.setString(1, username);
        return pstmt.executeQuery();
    }
    public static String assignUserToManager(String managerUsername, String username) throws Exception {
        String query = "UPDATE userdata SET manager_name = ? WHERE user_name = ?";
        try (Connection conn = connectDb(); 
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            
            pstmt.setString(1, managerUsername);
            pstmt.setString(2, username);
            int rowsUpdated = pstmt.executeUpdate();

            if (rowsUpdated > 0) {
                return "User successfully assigned to manager.";
            } else {
                return "No user found with the specified username.";
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "error: SQL error: " + e.getMessage();
        }
    }
    public static String getTeamMembers(String managerUsername) throws Exception {
        StringBuilder teamMembers = new StringBuilder();
        String query = "SELECT user_name FROM userdata WHERE manager_name = ?";

        try (Connection conn = connectDb(); 
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            
            pstmt.setString(1, managerUsername);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    if (teamMembers.length() > 0) {
                        teamMembers.append(",");
                    }
                    teamMembers.append(rs.getString("user_name"));
                }
            }
        }
        return teamMembers.toString();
    }
    public static String getManagerForUser(String username) throws Exception {
        String sql = "SELECT manager_name FROM userdata WHERE user_name = ?";
        
        try (Connection conn = connectDb(); 
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return rs.getString("manager_name");
            }
            return null; // No manager found
        }
    }
    public static String getApprovalRequests(String username) throws Exception {
        StringBuilder result = new StringBuilder();
        String sql = "SELECT 'Resource Request' AS request_type, resource_name AS request_name, approval_status " +
                     "FROM resource_requests WHERE username = ? " +
                     "UNION ALL " +
                     "SELECT 'Role Change' AS request_type, request_for AS request_name, approval_status " +
                     "FROM requestss WHERE request_by = ?";

        try (Connection conn = connectDb(); 
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, username);

            try (ResultSet rs = pstmt.executeQuery()) {
                result.append(String.format("%-20s %-30s %-10s%n", "Request Type", "Request Name", "Approval Status")); // Header

                while (rs.next()) {
                    String requestType = rs.getString("request_type");
                    String requestName = rs.getString("request_name");
                    String approvalStatus = rs.getString("approval_status");
                    result.append(String.format("%-20s %-30s %-10s%n", requestType, requestName, approvalStatus));
                }
            }
        }
        return result.toString();
    }
    public static void invalidateSession(HttpSession session) {
        if (session != null) {
            session.invalidate();
        }
    }
    public static boolean addUser(String firstName, String lastName, String email, String password, String username) throws Exception {
        String sql = "INSERT INTO userdata (first_name, last_name, email_address, user_type, h_password, join_date, user_name) VALUES (?, ?, ?, 'user', ?, CURDATE(), ?)";

        try (Connection conn = connectDb()) {
            if (conn == null) {
                throw new SQLException("Database connection failed");
            }

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                String encryptedPassword = encryptPassword(password);

                pstmt.setString(1, firstName);
                pstmt.setString(2, lastName);
                pstmt.setString(3, email);
                pstmt.setString(4, encryptedPassword);
                pstmt.setString(5, username);

                return pstmt.executeUpdate() > 0;
            }
        }
    }
    public static String resetPassword(String username, String email, String newPassword, String confirmPassword) throws Exception {
        int minPasswordLength = 8;
        int maxPasswordLength = 20;
        String passwordPattern = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@#$%^&+=]).{" + minPasswordLength + "," + maxPasswordLength + "}$";

        // Validate passwords match
        if (!newPassword.equals(confirmPassword)) {
            return "error:Passwords do not match";
        }

        // Validate password constraints
        if (!isPasswordValid(newPassword, passwordPattern, minPasswordLength, maxPasswordLength)) {
            return "error:Password must be between " + minPasswordLength + " and " + maxPasswordLength + " characters long, include at least one uppercase letter, one lowercase letter, one digit, and one special character.";
        }

        String sql = "UPDATE userdata SET h_password = ? WHERE user_name = ? AND email_address = ?";

        try (Connection conn = connectDb()) {
            if (conn == null) {
                return "error:Database connection failed";
            }

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                String encryptedPassword = encryptPassword(newPassword);

                pstmt.setString(1, encryptedPassword);
                pstmt.setString(2, username);
                pstmt.setString(3, email);

                int rowsAffected = pstmt.executeUpdate();
                if (rowsAffected > 0) {
                    return "success:Password has been reset successfully";
                } else {
                    return "error:Username or email does not match";
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "error:SQL error: " + e.getMessage();
        } catch (Exception e) {
            e.printStackTrace();
            return "error:Unexpected error: " + e.getMessage();
        }
    }
    public static int deleteUser(String username) throws Exception {
        String sqlUserData = "DELETE FROM userdata WHERE user_name = ?";
        String sqlResourceRequests = "DELETE FROM resource_requests WHERE username = ?";
        String sqlRequests = "DELETE FROM requestss WHERE request_by = ? OR request_for = ?";

        try (Connection conn = connectDb();
             PreparedStatement pstmtUserData = conn.prepareStatement(sqlUserData);
             PreparedStatement pstmtResourceRequests = conn.prepareStatement(sqlResourceRequests);
             PreparedStatement pstmtRequests = conn.prepareStatement(sqlRequests)) {

            // Start transaction
            conn.setAutoCommit(false);

            // Delete from userdata
            pstmtUserData.setString(1, username);
            int rowsUserData = pstmtUserData.executeUpdate();

            // Delete from resource_requests
            pstmtResourceRequests.setString(1, username);
            int rowsResourceRequests = pstmtResourceRequests.executeUpdate();

            // Delete from requestss
            pstmtRequests.setString(1, username);
            pstmtRequests.setString(2, username);
            int rowsRequests = pstmtRequests.executeUpdate();

            // Commit transaction
            conn.commit();

            // Return total rows affected
            return rowsUserData + rowsResourceRequests + rowsRequests;
        } catch (SQLException e) {
            // Rollback transaction in case of error
            try (Connection conn = connectDb()) {
                conn.rollback();
            } catch (SQLException rollbackEx) {
                rollbackEx.printStackTrace();
            }
            throw e;
        }
    }
   

    private static String encryptPassword(String password) {
        File file = new File("C:\\Manogna\\myproject\\uam\\src\\main\\webapp\\encp.txt");
        if (!file.exists()) {
            throw new RuntimeException("encp.txt not found");
        }
        Scanner sc = null;
        try {
            sc = new Scanner(file);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Failed to read encp.txt", e);
        }

        Map<Character, String> charMapping = new HashMap<>();
        int row = 1;

        while (sc.hasNextLine()) {
            String line = sc.nextLine();
            for (int col = 0; col < line.length(); col++) {
                char currentChar = line.charAt(col);
                String encryptedValue = row + "" + (col + 1);
                charMapping.put(currentChar, encryptedValue);
            }
            row++;
        }
        sc.close();

        StringBuilder encryptedPassword = new StringBuilder();

        for (char c : password.toCharArray()) {
            String encryptedValue = charMapping.get(c);
            if (encryptedValue != null) {
                encryptedPassword.append(encryptedValue);
            } else {
                encryptedPassword.append(c);
            }
        }

        return encryptedPassword.toString();
    }

  
}
