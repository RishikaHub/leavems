# LeaveMS - Team Availability and Leave Management Tool

LeaveMS is a Spring Boot modular monolith for submitting, approving, rejecting, cancelling, and tracking employee leave requests. It was built for a time-boxed assignment where correctness, acceptance criteria coverage, and clean backend structure matter more than UI polish.

## Project Status

The application supports the core leave management workflow:

- User registration and login
- JWT-based authentication using an HttpOnly cookie
- BCrypt password hashing
- Employee and manager roles
- Direct manager assignment
- Leave request creation and tracking
- Manager approval and rejection
- Leave balance deduction and restoration
- Team availability view for approved leave
- Filtering, sorting, searching, and pagination support on request lists
- SQL Server persistence through Spring Data JPA/Hibernate

## Tech Stack

- Java 21
- Spring Boot 4.0.6
- Spring MVC
- Spring Security
- Spring Data JPA
- Hibernate
- Thymeleaf
- Bootstrap 5
- Lombok
- Bean Validation
- JWT using `jjwt`
- Microsoft SQL Server
- Maven

## Why This Architecture

The project uses a modular monolith with layered architecture. This keeps the implementation simple enough for a single developer while still making the code easy to explain, test, and extend.

The code is organized by business capability rather than by technical type alone:

- `auth` owns login, registration, JWT, and security integration.
- `user` owns users, roles, dashboards, and direct manager relationships.
- `leave` owns the leave request lifecycle.
- `balance` owns available leave balance tracking.
- `availability` owns the approved-leave team availability view.
- `common` owns shared exceptions and web helpers.

## File Structure

```text
leavems/
├── pom.xml
├── README.md
├── mvnw
├── mvnw.cmd
└── src/
    ├── main/
    │   ├── java/com/company/leavems/
    │   │   ├── LeaveManagementApplication.java
    │   │   ├── auth/
    │   │   │   ├── controller/AuthController.java
    │   │   │   ├── dto/AuthResponse.java
    │   │   │   ├── dto/LoginRequest.java
    │   │   │   ├── dto/RegisterRequest.java
    │   │   │   ├── security/CustomUserDetailsService.java
    │   │   │   ├── security/JwtAuthenticationFilter.java
    │   │   │   ├── security/JwtUtil.java
    │   │   │   ├── security/SecurityConfig.java
    │   │   │   └── service/AuthService.java
    │   │   ├── availability/
    │   │   │   ├── controller/AvailabilityController.java
    │   │   │   ├── dto/AvailabilityResponse.java
    │   │   │   └── service/AvailabilityService.java
    │   │   ├── balance/
    │   │   │   ├── domain/LeaveBalance.java
    │   │   │   ├── repository/LeaveBalanceRepository.java
    │   │   │   └── service/LeaveBalanceService.java
    │   │   ├── common/
    │   │   │   ├── exception/BusinessException.java
    │   │   │   ├── exception/ForbiddenException.java
    │   │   │   ├── exception/GlobalExceptionHandler.java
    │   │   │   ├── exception/NotFoundException.java
    │   │   │   ├── web/CurrentUserService.java
    │   │   │   └── web/GlobalModelAttributes.java
    │   │   ├── leave/
    │   │   │   ├── controller/LeaveController.java
    │   │   │   ├── controller/ManagerLeaveController.java
    │   │   │   ├── domain/LeaveRequest.java
    │   │   │   ├── domain/LeaveStatus.java
    │   │   │   ├── domain/LeaveType.java
    │   │   │   ├── dto/CreateLeaveRequest.java
    │   │   │   ├── dto/DecisionRequest.java
    │   │   │   ├── dto/LeaveRequestResponse.java
    │   │   │   ├── dto/UpdateLeaveRequest.java
    │   │   │   ├── repository/LeaveRequestRepository.java
    │   │   │   └── service/LeaveRequestService.java
    │   │   └── user/
    │   │       ├── controller/DashboardController.java
    │   │       ├── domain/User.java
    │   │       ├── domain/UserRole.java
    │   │       └── repository/UserRepository.java
    │   └── resources/
    │       ├── application.yml
    │       └── templates/
    │           ├── auth/
    │           ├── availability/
    │           ├── fragments/
    │           ├── leave/
    │           └── manager/
    └── test/java/com/company/leavems/
        └── LeaveManagementApplicationTests.java
```

## Main Features

### Authentication

- Users can register with full name, email, password, role, and optional manager.
- Passwords are stored using BCrypt.
- Login creates a JWT and stores it in an HttpOnly cookie named `ACCESS_TOKEN`.
- Logout clears the cookie.
- Protected pages require authentication.

### Roles

The system supports two roles:

- `EMPLOYEE`
- `MANAGER`

Each employee can have one direct manager through a self-referencing `User.manager` relationship.

### Leave Request Lifecycle

Supported statuses:

- `PENDING`
- `APPROVED`
- `REJECTED`
- `CANCELLED`

Employees can:

- Create leave requests
- View their own leave requests
- Withdraw pending requests
- Cancel approved future requests

Managers can:

- View requests from direct reports
- Approve pending requests
- Reject pending requests
- Add manager notes

### Leave Types

Supported leave types:

- `VACATION`
- `SICK`
- `PERSONAL`

### Balance Management

Default balances on registration:

- Vacation: 20 days
- Sick: 10 days
- Personal: 5 days

Rules:

- Balance is checked when creating a request.
- Balance is not deducted when a request is created.
- Balance is deducted only on approval.
- Balance is restored when an approved future request is cancelled.
- Rejected and withdrawn requests do not change balance.

### Validation Rules

The service layer enforces:

- Start date and end date are required.
- End date must be the same as or after start date.
- Leave type is required.
- Overlapping pending or approved requests are rejected.
- Insufficient balance is rejected.
- Only the requester can withdraw or cancel their own request.
- Only the direct manager can approve or reject a request.
- Approval re-checks balance at decision time.

The create request page also sets the end date picker minimum based on the selected start date so earlier dates are disabled in the browser.

### Team Availability

The availability page shows approved leave across a selected date range.

It intentionally exposes only:

- Employee name
- Start date
- End date

It hides:

- Leave type
- Reason
- Manager note

### Filtering, Sorting, Search, Pagination

The request list supports filtering by:

- Status
- Leave type

Manager request list also supports searching by employee name.

Sorting is supported for:

- Start date
- End date
- Created date
- Status

Pagination is implemented through Spring Data `Pageable`.

## Database

The project currently uses Microsoft SQL Server because it was available locally through SSMS.

Expected database:

```text
Database name: leavems
Server: local SQL Server instance
```

The application uses Hibernate `ddl-auto: update` for fast local development.

## Setup Instructions

### Prerequisites

- Java 21
- Maven or Maven Wrapper
- Microsoft SQL Server
- SSMS or another SQL Server client

### Create Database

In SSMS:

```sql
CREATE DATABASE leavems;
GO
```

### Configure Database Password

`application.yml` uses an environment variable for the database password:

```yaml
password: ${DB_PASSWORD:change-me}
```

Set `DB_PASSWORD` before running, or temporarily replace `change-me` locally with your SQL Server password.

### Run

From the project root:

```cmd
.\mvnw.cmd spring-boot:run
```

Open:

```text
http://localhost:8080
```

Useful pages:

```text
/auth/register
/auth/login
/dashboard
/leave/requests
/leave/requests/new
/manager/requests
/availability
```

## Suggested Demo Flow

1. Register a manager.
2. Register an employee and assign the manager.
3. Login as employee.
4. Create a leave request.
5. Verify it appears as pending.
6. Logout and login as manager.
7. Open manager requests.
8. Approve or reject the request.
9. Verify employee balance changes only after approval.
10. Open availability and confirm only approved leave is shown.

## Important Business Assumptions

- Day count uses inclusive calendar days.
- Weekends and holidays are not excluded.
- No half-day leave support.
- No leave accruals.
- No carry-over rules.
- No HR/admin role.
- No email or notification integration.
- Managers are assigned during registration.

## Challenges Faced

- PostgreSQL was originally planned, but local installation required admin rights. The project was adapted to SQL Server because SSMS/SQL Server was already available.
- Thymeleaf templates needed careful alignment with DTO field names to avoid runtime template errors.
- JWT authentication with Thymeleaf required using an HttpOnly cookie rather than browser local storage.
- Manager authorization had to be enforced in the service layer, not only by hiding UI buttons.
- Balance handling needed transaction-safe logic so approval and balance deduction remain consistent.
- Date validation had to be implemented both on the backend and in the browser to prevent invalid leave ranges.

## Trade-offs

Given the time limit, the project prioritizes correctness and business rules over UI polish.

Deliberately skipped or simplified:

- React frontend
- Microservices
- Docker setup
- Email notifications
- Admin screens
- Holiday-aware business day logic
- Half-day leave
- CSV/iCal exports
- Production deployment
- Refresh token flow

## Future Improvements

- Add Flyway migrations.
- Add integration tests with Testcontainers.
- Add a dedicated admin role for manager assignment and balance corrections.
- Improve UI consistency and accessibility.
- Add better audit history for status changes.
- Add email notifications for approvals/rejections.
- Add business-day calculation and holiday calendars.
- Add CSV export for leave history.
- Move secrets fully out of `application.yml` for production deployments.

## AI Usage Disclosure

AI assistance was used for architecture planning, code scaffolding, Thymeleaf page corrections, validation fixes, and README generation. The generated code was reviewed and adjusted during implementation.

## Repository

```text
https://github.com/RishikaHub/leavems
```

## Automated Test Coverage

The project includes focused unit tests for the core leave-management business rules in `LeaveRequestServiceTest`.

Covered by tests:

- Valid leave request creates a `PENDING` request.
- End date before start date is rejected.
- Overlapping active leave is rejected.
- Insufficient balance is rejected.
- Balance is not deducted on request creation.
- Direct manager can approve a request.
- Non-direct manager cannot approve a request.
- Approval re-checks current balance.
- Approval deducts balance.
- Rejection records decision metadata and does not change balance.
- Pending withdrawal cancels the request without changing balance.
- Approved future cancellation restores balance.
- Request detail visibility allows only requester or direct manager.
- Manager team list requires the manager role.
- Manager filters, search, and pagination are delegated to the repository layer.

Run all tests:

```cmd
.\mvnw.cmd test
```

Run only the leave request service tests:

```cmd
.\mvnw.cmd -Dtest=LeaveRequestServiceTest test
```

Current test result at the time of writing:

```text
Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```
