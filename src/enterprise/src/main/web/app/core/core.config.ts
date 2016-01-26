/// <reference path="../../typings/tsd.d.ts" />

module app.core {
	class Config {
		constructor() {
			toastr.options.timeOut = 4000;
			toastr.options.positionClass = 'toast-bottom-right';
		}
	}

	angular
		.module('app.core')
		.config(Config);
}