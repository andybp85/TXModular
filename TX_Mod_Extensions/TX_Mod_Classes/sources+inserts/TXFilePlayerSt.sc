// Copyright (C) 2005  Paul Miller. This file is part of TX Modular system distributed under the terms of the GNU General Public License (see file LICENSE).

TXFilePlayerSt : TXModuleBase {	// Disk File Player - stereo

	classvar <>arrInstances;	
	classvar <defaultName;  		// default module name
	classvar <moduleRate;			// "audio" or "control"
	classvar <moduleType;			// "source", "insert", "bus",or  "channel"
	classvar <noInChannels;			// no of input channels 
	classvar <arrAudSCInBusSpecs; 	// audio side-chain input bus specs 
	classvar <>arrCtlSCInBusSpecs; 	// control side-chain input bus specs
	classvar <noOutChannels;		// no of output channels 
	classvar <arrOutBusSpecs; 		// output bus specs
	classvar	<arrBufferSpecs;
	classvar	<guiWidth=500;
	
	var sampleFileName = "";
	var sampleNumChannels = 0;

*initClass{
	arrInstances = [];		
	//	set class specific variables
	defaultName = "File Player St";
	moduleRate = "audio";
	moduleType = "groupsource";
	arrCtlSCInBusSpecs = [ 		
		["Level", 1, "modLevel", 0],
	];	
	noOutChannels = 2;
	arrOutBusSpecs = [ 
		["Out L + R", [0,1]], 
		["Out L only", [0]], 
		["Out R only", [1]] 
	];	
	arrBufferSpecs = [ ["bufnumLoop", 32768, 2] ];
} // end of method initClass

*new{ arg argInstName;
	 ^super.new.init(argInstName);
} 

init {arg argInstName;
	//	set  class specific instance variables
	extraLatency = 0.2;	// allow extra time when recreating
	midiListen = 0;	// don't respond to midi input
	arrSynthArgSpecs = [
		["out", 0, 0],
		["gate", 1, 0],
		["note", 0, \ir],
		["velocity", 0, \ir],
		["bufnumSound", 0, \ir],
		["level", 0.5, defLagTime],
		["attack", 0.01, defLagTime],
		["release", 0.01, defLagTime],
		["modLevel", 0, defLagTime],
  	]; 
	synthDefFunc = { 
		arg out, gate, note, velocity, bufnumSound, level, attack, release, modLevel;
		var outEnv, outSound, outLevel;
		
		outEnv = EnvGen.kr(
			Env.new([0,1,1,0],[attack, 1, release],'sine', 2),
			gate, 
			doneAction: 2
		);
		outLevel = (level + modLevel).max(0).min(1);
		outSound = DiskIn.ar(2, bufnumSound) * outLevel;
		Out.ar(out, outEnv * outSound);
	};
	guiSpecArray = [
		["OpenFileButton", 
			{arg argPath;  
				if (this.isValidSoundFile(argPath),  {this.cueSample(argPath)}); 
				sampleFileName;    // function returns sampleFileName
			}, 
			{sampleFileName}
		], 
		["DividingLine"], 
		["EZslider", "Fade in", ControlSpec(0, 20), "attack"], 
		["EZslider", "Fade out", ControlSpec(0, 20), "release"], 
		["DividingLine"], 
		["EZslider", "Level", ControlSpec(0, 1), "level"], 
		["DividingLine"], 
		["ActionButtonBig", "Rewind", {
			moduleNode.release(0); 
			this.reCueSample;
		}], 
		["Spacer", 3], 
		["ActionButtonBig", "Play", {moduleNode.release(0); this.createSynthNote(60, 127, 0); }], 
		["Spacer", 3], 
		["ActionButtonBig", "Fade out", {moduleNode.release; }], 
		["Spacer", 3], 
		["ActionButtonBig", "Stop", {moduleNode.release(0); }], 
		["DividingLine"], 
	];
	arrActionSpecs = this.buildActionSpecs([
		["commandAction", "Play", {
			moduleNode.release(0); 
			this.createSynthNote(60, 127, 0); }],
		["commandAction", "Play from start", {
			moduleNode.release(0); 
			this.reCueSample;
			this.createSynthNote(60, 127, 0);
		}],
		["commandAction", "Stop with fade out", {moduleNode.release; }],
		["commandAction", "Stop immediately", {moduleNode.release(0); }], 
		["commandAction", "Rewind", {
			moduleNode.release(0); 
			this.reCueSample;
		}],
		["EZslider", "Fade in", ControlSpec(0, 20), "attack"], 
		["EZslider", "Fade out", ControlSpec(0, 20), "release"], 
		["EZslider", "Level", ControlSpec(0, 1), "level"], 
	]);	

	// use base class initialise 
	this.baseInit(this, argInstName);
	this.midiNoteInit;
	//	make buffers, load the synthdef and create the Group for synths to belong to
	this.makeBuffersAndGroup(arrBufferSpecs);
} // end of method init

extraSaveData { // override default method
	^[sampleFileName];
}

loadExtraData {arg argData;  // override default method
	if (sampleFileName != argData.at(0), {
		sampleFileName = argData.at(0);
		Routine.run {
			var holdModCondition;
			// add condition to load queue
			holdModCondition = system.holdLoadQueue.addCondition;
			// pause
			holdModCondition.wait;
			system.server.sync;
			// cue sample
			this.cueSample(sampleFileName);
			// remove condition from load queue
			system.holdLoadQueue.removeCondition(holdModCondition);
		};
	});
}

allNotesOff { 
	//	override superclass method 
	// take no action
}

isValidSoundFile {arg argPath;  // check argument is a valide stereo path
	var holdFile, errorMessage;
	holdFile = SoundFile.new;
	if (holdFile.openRead(argPath), {
		if (holdFile.numChannels != 2, {
			errorMessage = "Error: file should be stereo. No. channels: " ++ holdFile.numChannels.asString;
		});
	}, {
		errorMessage = "Error: invalid file " ++ argPath;
	});
	holdFile.close;
	if (errorMessage.notNil, {
		TXInfoScreen(errorMessage); 
		^false;
	});
	^true;
}

cueSample { arg argFileName; // method to cue sample into buffer
	var holdBuffer;
	sampleFileName = "";
	sampleNumChannels = 0;
	// close first before cueing
	if (buffers.at(0).notNil, {buffers.at(0).close});
	Routine.run {
		var holdModCondition;
		// add condition to load queue
		holdModCondition = system.holdLoadQueue.addCondition;
		// pause
		holdModCondition.wait;
		system.server.sync;
		if (argFileName.isNil, {
			// if argFileName is nil, clear the current buffer & filename
			buffers.at(0).zero;
		},{
			// otherwise,  try to cue file.  if it fails, display error message and clear
			holdBuffer = Buffer.cueSoundFile(system.server, argFileName, 0, 2, 32768, { 
				arg argBuffer; 
				//	if file loaded ok
				if (argBuffer.notNil, {
					sampleFileName = argFileName;
					sampleNumChannels = argBuffer.numChannels;
					buffers.put(0, argBuffer);
					// store current data to synthArgSpecs
					this.setSynthArgSpec("bufnumSound", argBuffer.bufnum);
				},{
					buffers.at(0).zero;
					{TXInfoScreen.new("Invalid Sample File" ++ argFileName);}.defer; // defer because gui process
				});
			});
		});
		// remove condition from load queue
		system.holdLoadQueue.removeCondition(holdModCondition);
		// update view
		system.showView;
	};
} // end of method cueSample

reCueSample { // method to re-cue sample into buffer from start of file
	// close first before cueing
	buffers.at(0).close;
	buffers.at(0).cueSoundFile(sampleFileName,0);
} // end of method reCueSample

}
