Console based Bank management system using Java 8 , SpringBoot , SpringSecurity etc

Password Policy


Minimum 8 characters
At least one uppercase letter
At least one digit
At least one special character (!@#$%^&* etc.)
Last 3 passwords are remembered , cannot be reused


Features

Authentication & Security


Login with username and password
BCrypt hashed passwords (never stored in plain text)
JWT session tokens verified on every action
5 failed login attempts , account locked for 15 minutes
Session expires after 30 minutes
Full audit log of all login attempts, failures, lockouts, and password changes

Multi-Account Support


One customer login can hold multiple accounts (Savings + Current)
After login, customer sees all their accounts and picks an active one
Can switch active account at any time from the menu
Transfers can be made between their own accounts or to others


Account Management


Create SAVINGS or CURRENT accounts with optional initial deposit
View account details
List all accounts (Admin/Teller)
Update account name, email, phone
Freeze or activate accounts
Close accounts (balance must be zero first)


Transactions


Deposit (Admin/Teller only)
Withdraw
Transfer between any two accounts
Full transaction history with before/after balances
View last N transactions


User Management (Admin/Teller)


Create Teller users (Admin only)
Create Customer users linked to a bank account
Link additional accounts to an existing customer login
Deactivate users
Unlock locked users
View all users with their linked accounts


Admin Tools


Audit logs — all security events with timestamps
Bank summary — total accounts, active accounts, total funds
