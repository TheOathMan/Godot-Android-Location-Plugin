class_name LocationAndroid
extends Node

signal location_updated(Latitude:float,Longitude:float)
signal location_status_changed(location_status:LocationServiceStatus)
signal authorization_status_changed(authorization_status:AuthorizationStatus)
signal dialogue_appeal_result_updated(result:DialogueResultUpdated)

enum AuthorizationStatus{
		PERMISSION_DENIED  = 0,
		PERMISSION_GRANTED = 1
}

enum LocationServiceStatus{
	IDLE       = 0,
	ENABLED    = 1,
	DISABLED   = 2,
	STOPPED    = 3
}

enum DialogueResultUpdated{
	POSITIVE = 1,
	NEGATIVE = 0
}

static var current_authorization_status: AuthorizationStatus
static var current_location_service_status: LocationServiceStatus
var _location_plugin

# Main method to call to start location service and handle clint followed up results.
#   - If user allowed location access, coordinates will be sent to signal location_updated
#   - If user denied location access, an appeal dialogue will be showen 
func begin_Android_location_service():
	if current_authorization_status == AuthorizationStatus.PERMISSION_GRANTED:
		StartLocationService()
	else:
		authorization_status_changed.connect(func(s:AuthorizationStatus):
			if s & AuthorizationStatus.PERMISSION_GRANTED:
				StartLocationService()
			)
		AskLocationPermission()


func get_plugin():
	if OS.get_name() != "Android":
		printerr("Wrong operating system for LocationAndroid Plugin")
		return dummy;
	return _location_plugin


func _ready():
	if Engine.has_singleton("AndroidLocationPlugin"):
		_location_plugin = Engine.get_singleton("AndroidLocationPlugin")
		_location_plugin.connect("LocationUpdated",_location_updated)
		_location_plugin.connect("AuthorizationStatusUpdated",_authorization_status_changed)
		_location_plugin.connect("LocationStatusUpdated",_location_status_changed)
		_location_plugin.connect("DialogueResultUpdated",_dialogue_appeal_result_updated)

		#optional: show Location Permission Appeal dialogue if the user denied location access.
		authorization_status_changed.connect(func(s:AuthorizationStatus):
			if s == AuthorizationStatus.PERMISSION_DENIED:
				ShowLocationPermissionAppeal()
		)
		#---------
	else:
		printerr("Failed to initialization Android Location Plugin")
	pass # Replace with function body.
	
func _location_updated(lat:float,lon:float):
	location_updated.emit(lat,lon)

func _authorization_status_changed(status:int):
	authorization_status_changed.emit(status)
	current_authorization_status=status

func _location_status_changed(status:int):
	location_status_changed.emit(status)
	
func _dialogue_appeal_result_updated(result:int):
	dialogue_appeal_result_updated.emit(result)
	

#-------raw calls. Results are sent to signals but followed up user interactions are unhandled

# The resulting signal of this call will be sent to location_status_changed.
# also call to get the current_location_service_status 
func StartLocationService()->void:
	get_plugin().StartLocationService()

# it is generally a good practice to stop location updates when they are no longer needed.
# This helps to conserve battery life and reduce unnecessary resource usage.
func StopLocationSerivce() -> void:
	get_plugin().stopLocationUpdates()

# The resulting signal of this call will be sent to authorization_status_changed.
# also call to get the current_location_service_status as a signal update
func AskLocationPermission() -> void:
	get_plugin().requestLocationPermissions()

# The resulting signal of this call will be sent to dialogue_appeal_result.
func ShowLocationPermissionAppeal() -> void:
	get_plugin().ShowLocationPermissionAppeal("Locatoin Access",
	"Please enable location access from settings to allow "+ ProjectSettings.get_setting("application/config/name") +" to provide location-based features and services")
	

class dummy:
	static func StartLocationService()-> void:pass
	static func requestLocationPermissions()-> void:pass
	static func ShowLocationPermissionAppeal(l,r) -> void:pass
