package tn.oliveconnect.user;

import org.springframework.data.mongodb.core.index.Indexed;

/** Cooperative details of a Farmer, embedded in the User document. */
public class FarmerProfile {

    // unique + sparse: Users without a profile are not indexed (index on farmerProfile.memberNumber)
    @Indexed(unique = true, sparse = true)
    private String memberNumber;
    private String cin;
    private String address;
    private Delegation delegation;

    public String getMemberNumber() { return memberNumber; }
    public void setMemberNumber(String memberNumber) { this.memberNumber = memberNumber; }
    public String getCin() { return cin; }
    public void setCin(String cin) { this.cin = cin; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public Delegation getDelegation() { return delegation; }
    public void setDelegation(Delegation delegation) { this.delegation = delegation; }
}
