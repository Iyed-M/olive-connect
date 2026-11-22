// The official Mongo image runs this file only when the data directory is new.
// Keeping all application users here makes the first VPS start deterministic.

function createUserIfMissing(databaseName, user, password, roles) {
    const database = db.getSiblingDB(databaseName);
    try {
        database.createUser({user: user, pwd: password, roles: roles});
    } catch (error) {
        // A restart reuses the existing data directory and must not fail because
        // the user already exists.
        if (error.codeName !== "DuplicateKey" && error.code !== 51003) {
            throw error;
        }
    }
}

const appUser = process.env.MONGO_APP_USER || "oliveconnect";
const appPassword = process.env.MONGO_APP_PASSWORD;
const exporterUser = process.env.MONGO_EXPORTER_USER || "exporter";
const exporterPassword = process.env.MONGO_EXPORTER_PASSWORD;
const backupUser = process.env.MONGO_BACKUP_USER || "backup";
const backupPassword = process.env.MONGO_BACKUP_PASSWORD;

if (!appPassword || !exporterPassword || !backupPassword) {
    throw new Error("Mongo application, exporter and backup passwords are required");
}

createUserIfMissing("oliveconnect", appUser, appPassword, [
    {role: "readWrite", db: "oliveconnect"}
]);

createUserIfMissing("admin", exporterUser, exporterPassword, [
    {role: "clusterMonitor", db: "admin"},
    {role: "read", db: "local"}
]);

createUserIfMissing("admin", backupUser, backupPassword, [
    {role: "backup", db: "admin"}
]);
