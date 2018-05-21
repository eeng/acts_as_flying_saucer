# ActsAsFlyingSaucer
module ActsAsFlyingSaucer

	module Controller
		def self.included(base)
			base.extend(ClassMethods)
		end

		private

		# ClassMethods
		#
		module ClassMethods

			# acts_as_flying_saucer
			#
			def acts_as_flying_saucer
				self.send(:include, ActsAsFlyingSaucer::Controller::InstanceMethods)
				class_eval do
					attr_accessor :pdf_mode
				end
			end
		end

		# InstanceMethods
		#
		module InstanceMethods
			# render_pdf
			#
			def render_pdf(options = {})
       tidy_clean = options[:clean] || false
				self.pdf_mode = :create
        if options[:url]
          tidy_clean = true
          if options[:url].match(/\Ahttp/)
            html = Net::HTTP.get_response(URI.parse(options[:url])).body rescue options[:url]
          elsif File.exist?(options[:url])
            html =  File.read(options[:url]) rescue  ""
          else
            html = options[:url]
          end
        elsif defined?(Rails)
          host = ActionController::Base.asset_host
        	ActionController::Base.asset_host = request.protocol + request.host_with_port if host.blank?
					html = render_to_string options
					if options[:debug_html] || params[:debug].present? || Rails.env.test?
						#    ActionController::Base.asset_host = host
						response.header["Content-Type"] = "text/html; charset=utf-8"
						render :html => html and return
					end
					#sinatra
				elsif defined?(Sinatra)
					html = options[:template]
					if options[:debug_html]
						response.header["Content-Type"] = "text/html; charset=utf-8"
						response.body << html and return
					end
				end
				# saving the file

				options[:tidy_clean] = tidy_clean
				options[:cache] = params[:cache] || (Rails.env.development? ? 'false' : 'true')
        options[:silent_print] = params[:silent_print] ? Boolean(params[:silent_print]) : Boolean(options[:silent_print])
				output_file = HtmlToPdfConverter.convert html, options
				# restoring the host
				if defined?(Rails)
				 ActionController::Base.asset_host = host
				end

				# sending the file to the client
        if options[:send_to_client] == false
          output_file
        else
				  send_file_options = {
								  :filename => File.basename(output_file),
								  :disposition => 'inline',
								  :type => 'application/pdf'
								  #:x_sendfile => true,
				  }
				  send_file_options.merge!(options[:send_file]) if options.respond_to?(:merge) && options[:send_file]
				  send_file(output_file, send_file_options)
        end
			end
		end
	end
end