# OliveConnect

An olive-growing cooperative in the Sfax region coordinates harvest collection between its member farmers, its field teams, its equipment and the mills.

## Language

### People and access

**User**:
A person who can log in to OliveConnect, identified by their email.
_Avoid_: Account, login, member (as a synonym)

**Role**:
A responsibility a User holds in the cooperative (Farmer, Collection manager, Team leader, Logistics manager, Mill manager, Admin). A User can hold several Roles.
_Avoid_: Profile, user type, permission

**Active role**:
The Role whose home and navigation a multi-role User is currently looking at. It changes what they see, never what they are allowed to do.
_Avoid_: Current profile, mode

**Role home**:
The page a User lands on for a given Role after logging in.
_Avoid_: Dashboard (reserved for the KPI pages), landing page

**Farmer**:
A User with the Farmer Role who owns orchards. Every Farmer is a Member of the cooperative.
_Avoid_: Grower, producer, owner (as a role name)

**Member**:
A person who belongs to the cooperative, identified by their member number.
_Avoid_: Adherent, subscriber

**Farmer profile**:
The cooperative-specific details of a Farmer: member number, national ID (CIN), address and delegation.
_Avoid_: Farmer account

**Admin**:
A User with the Admin Role who manages Users and Roles.
_Avoid_: Superuser, root

**Registration request**:
A visitor's request to join the cooperative. Approving it creates a Farmer. Until then the visitor is neither a User nor a Farmer.
_Avoid_: Sign-up, pending farmer

**Delegation**:
An administrative district of the Sfax governorate (for example Sakiet Ezzit). Farmers and orchards each belong to one Delegation.
_Avoid_: Zone, region, area

**Correction**:
A manual fix made by staff on an orchard, an equipment or a COMPLETED round, kept with its date, author and comment in the history of the corrected document. A normal edit of a PLANNED round, or the owner editing their own orchard, is not a Correction.
_Avoid_: Amendment, audit log
